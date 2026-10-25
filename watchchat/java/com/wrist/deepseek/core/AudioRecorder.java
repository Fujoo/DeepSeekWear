package com.wrist.deepseek.core;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 16 kHz mono recorder with automatic gain, a gentle noise gate and a tiny
 * energy VAD. Quiet (whispered) speech gets up to ~31 dB of clean gain before
 * it is sent to ASR, which is what makes the watch microphone usable at all.
 */
public class AudioRecorder {
    private static final String TAG = "AudioRecorder";
    private static final int RATE = 16000;
    private static final int FRAME = 320; // 20 ms
    private static final int MAX_SECONDS = 300;

    public interface Listener {
        void onLevel(float level, long elapsedMs);

        void onFinished(byte[] wav, long durationMs, boolean hadSpeech);

        void onError(String message);
    }

    private final Context context;
    private final Listener listener;
    private volatile boolean running;
    private volatile boolean stopRequested;
    private AudioRecord record;
    private Thread thread;
    private boolean whisper;
    private long startedAt;
    private byte[] result;
    private long resultMs;
    private boolean resultSpeech;
    private String lastError;

    public AudioRecorder(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public synchronized boolean start(boolean whisperMode) {
        if (running) return false;
        whisper = whisperMode;
        stopRequested = false;
        result = null;
        resultMs = 0;
        resultSpeech = false;
        lastError = null;

        int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) min = 4096;
        int bufSize = Math.max(min * 2, FRAME * 8 * 2);
        try {
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize);
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                releaseRecord();
                record = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize);
            }
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                releaseRecord();
                lastError = "麦克风初始化失败，请检查录音权限";
                return false;
            }
        } catch (Throwable t) {
            releaseRecord();
            lastError = "无法打开麦克风：" + t.getMessage();
            return false;
        }

        running = true;
        startedAt = System.currentTimeMillis();
        thread = new Thread(this::recordLoop, "voice-recorder");
        thread.start();
        return true;
    }

    private void recordLoop() {
        short[] buf = new short[FRAME];
        ByteArrayOutputStream pcm = new ByteArrayOutputStream(RATE * 2 * 8);
        List<Float> levels = new ArrayList<>();
        double noiseFloor = 60;
        boolean speech = false;
        int above = 0;
        long lastVoiceAt = 0;
        long speechStart = 0;
        long lastLevelAt = 0;
        int frames = 0;
        boolean autoStopped = false;

        try {
            record.startRecording();
            if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                lastError = "麦克风被占用，请稍后重试";
                return;
            }
            while (!stopRequested) {
                int read = record.read(buf, 0, FRAME);
                if (read <= 0) {
                    if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_DEAD_OBJECT) {
                        lastError = "录音中断，请重试";
                    }
                    break;
                }
                long now = System.currentTimeMillis();
                long elapsed = now - startedAt;
                double rms = rms(buf, read);
                frames++;

                // Calibrate from the quietest of the first frames, not their
                // average: people often start whispering immediately.
                if (frames == 1) {
                    noiseFloor = Math.max(18, rms);
                } else if (frames <= 12) {
                    noiseFloor = Math.min(noiseFloor, Math.max(18, rms));
                }

                double threshold = whisper
                        ? Math.max(55, noiseFloor * 1.75 + 25)
                        : Math.max(140, noiseFloor * 3.0 + 60);

                if (rms > threshold) {
                    above++;
                    lastVoiceAt = now;
                } else {
                    above = 0;
                    if (!speech) {
                        noiseFloor = Math.max(18, Math.min(2400, noiseFloor * 0.985 + rms * 0.015));
                    }
                }

                if (!speech && above >= 3) {
                    speech = true;
                    speechStart = now;
                }

                // Keep the whole take (including pre-roll) but stop on silence.
                for (int i = 0; i < read; i++) {
                    short v = buf[i];
                    pcm.write(v & 0xff);
                    pcm.write((v >> 8) & 0xff);
                }

                float level = (float) Math.min(1.0, Math.pow(rms / 2800.0, 0.55));
                levels.add(level);
                if (now - lastLevelAt >= 55) {
                    lastLevelAt = now;
                    listener.onLevel(level, elapsed);
                }

                if (speech) {
                    long silence = now - lastVoiceAt;
                    long limit = whisper ? 2400 : 1600;
                    if (silence > limit) {
                        autoStopped = true;
                        break;
                    }
                } else if (elapsed > (whisper ? 11000 : 8000)) {
                    autoStopped = true;
                    break;
                }
                if (elapsed > MAX_SECONDS * 1000L) {
                    autoStopped = true;
                    break;
                }
            }
        } catch (Throwable t) {
            if (!stopRequested) lastError = "录音失败：" + t.getMessage();
        } finally {
            try {
                if (record != null && record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop();
                }
            } catch (Throwable ignored) {
            }
            releaseRecord();
            running = false;
        }

        if (stopRequested && lastError == null) {
            // Manual stop: normal path.
        }
        if (lastError != null && pcm.size() == 0) {
            listener.onError(lastError);
            return;
        }

        byte[] raw = pcm.toByteArray();
        short[] samples = bytesToShorts(raw);
        int[] trim = trim(samples, speech, whisper);
        int from = trim[0];
        int to = trim[1];
        int len = Math.max(0, to - from);
        long duration = len * 1000L / RATE;
        boolean hadSpeech = speech && len > RATE / 8;

        if (len < RATE / 8) {
            listener.onError(speech ? "录音太短，再说一次吧" : "没有听到声音，靠近手表再说一次");
            return;
        }

        short[] cut = new short[len];
        System.arraycopy(samples, from, cut, 0, len);
        short[] processed = process(cut, whisper, noiseFloor);
        result = wav(processed);
        resultMs = duration;
        resultSpeech = hadSpeech;
        writeDebugWav(result, levels, duration, hadSpeech, whisper, noiseFloor);
        listener.onFinished(result, resultMs, resultSpeech);
    }

    private static double rms(short[] b, int n) {
        if (n <= 0) return 0;
        double sum = 0;
        for (int i = 0; i < n; i++) {
            double v = b[i];
            sum += v * v;
        }
        return Math.sqrt(sum / n);
    }

    private static short[] bytesToShorts(byte[] b) {
        int n = b.length / 2;
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            out[i] = (short) ((b[i * 2] & 0xff) | (b[i * 2 + 1] << 8));
        }
        return out;
    }

    /** Returns [fromSample, toSample] with a little breathing room. */
    private static int[] trim(short[] s, boolean speech, boolean whisper) {
        if (s.length == 0) return new int[]{0, 0};
        if (!speech) return new int[]{0, s.length};
        double threshold = whisper ? 70 : 180;
        int first = -1, last = -1;
        for (int pos = 0; pos + FRAME <= s.length; pos += FRAME) {
            double r = 0;
            for (int i = 0; i < FRAME; i++) {
                double v = s[pos + i];
                r += v * v;
            }
            r = Math.sqrt(r / FRAME);
            if (r > threshold) {
                if (first < 0) first = pos;
                last = pos + FRAME;
            }
        }
        if (first < 0) return new int[]{0, s.length};
        int from = Math.max(0, first - RATE * 300 / 1000);
        int to = Math.min(s.length, last + RATE * 350 / 1000);
        return new int[]{from, to};
    }

    private static short[] process(short[] in, boolean whisper, double noiseFloor) {
        int n = in.length;
        short[] out = new short[n];
        double peak = 1;
        for (short v : in) peak = Math.max(peak, Math.abs((int) v));
        double target = 32767.0 * 0.82;
        double maxGain = whisper ? 40.0 : 13.0;
        double gain = Math.min(maxGain, target / peak);
        if (gain < 1.0) gain = 1.0;

        // 70 Hz one-pole high-pass: removes handling/rumble without hurting
        // the consonant energy ASR needs.
        double a = Math.exp(-2.0 * Math.PI * 70.0 / RATE);
        double prevIn = 0, prevOut = 0;
        double gateFloor = Math.max(30, noiseFloor * (whisper ? 0.75 : 1.15));
        double smoothed = 1.0;
        int fade = RATE / 50; // 20 ms
        double tanhNorm = Math.tanh(1.1);

        for (int i = 0; i < n; i++) {
            double x = in[i];
            double hp = a * (prevOut + x - prevIn);
            prevIn = x;
            prevOut = hp;

            if ((i % FRAME) == 0) {
                double r = 0;
                int end = Math.min(n, i + FRAME);
                for (int j = i; j < end; j++) r += in[j] * (double) in[j];
                r = Math.sqrt(r / Math.max(1, end - i));
                double targetGate;
                if (r < gateFloor) {
                    targetGate = whisper ? 0.62 : 0.38;
                } else {
                    targetGate = 1.0;
                }
                smoothed = smoothed * 0.55 + targetGate * 0.45;
            }

            double v = hp * gain * smoothed / 32768.0;
            v = Math.tanh(v * 1.1) / tanhNorm;
            if (i < fade) v *= (i / (double) fade);
            if (i > n - fade) v *= ((n - i) / (double) fade);

            int iv = (int) Math.round(v * 32767.0);
            if (iv > 32767) iv = 32767;
            if (iv < -32768) iv = -32768;
            out[i] = (short) iv;
        }
        return out;
    }

    private static byte[] wav(short[] samples) {
        int dataLen = samples.length * 2;
        byte[] out = new byte[44 + dataLen];
        int p = 0;
        out[p++] = 'R'; out[p++] = 'I'; out[p++] = 'F'; out[p++] = 'F';
        writeInt(out, p, 36 + dataLen); p += 4;
        out[p++] = 'W'; out[p++] = 'A'; out[p++] = 'V'; out[p++] = 'E';
        out[p++] = 'f'; out[p++] = 'm'; out[p++] = 't'; out[p++] = ' ';
        writeInt(out, p, 16); p += 4;
        writeShort(out, p, 1); p += 2;
        writeShort(out, p, 1); p += 2;
        writeInt(out, p, RATE); p += 4;
        writeInt(out, p, RATE * 2); p += 4;
        writeShort(out, p, 2); p += 2;
        writeShort(out, p, 16); p += 2;
        out[p++] = 'd'; out[p++] = 'a'; out[p++] = 't'; out[p++] = 'a';
        writeInt(out, p, dataLen); p += 4;
        for (short s : samples) {
            out[p++] = (byte) (s & 0xff);
            out[p++] = (byte) ((s >> 8) & 0xff);
        }
        return out;
    }

    private static void writeInt(byte[] b, int p, int v) {
        b[p] = (byte) (v & 0xff);
        b[p + 1] = (byte) ((v >> 8) & 0xff);
        b[p + 2] = (byte) ((v >> 16) & 0xff);
        b[p + 3] = (byte) ((v >> 24) & 0xff);
    }

    private static void writeShort(byte[] b, int p, int v) {
        b[p] = (byte) (v & 0xff);
        b[p + 1] = (byte) ((v >> 8) & 0xff);
    }

    private void writeDebugWav(byte[] wav, List<Float> levels, long duration,
                               boolean speech, boolean whisper, double floor) {
        try {
            File dir = context.getCacheDir();
            try (FileOutputStream f = new FileOutputStream(new File(dir, "last_voice.wav"))) {
                f.write(wav);
            }
            StringBuilder b = new StringBuilder();
            b.append("duration=").append(duration).append("ms speech=").append(speech)
                    .append(" whisper=").append(whisper).append(" floor=").append(Math.round(floor))
                    .append(" bytes=").append(wav.length).append("\nlevels=");
            for (int i = 0; i < levels.size(); i++) {
                if (i % 4 == 0) b.append(String.format(java.util.Locale.US, "%.2f ", levels.get(i)));
            }
            try (FileOutputStream f = new FileOutputStream(new File(dir, "last_voice.txt"))) {
                f.write(b.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Throwable t) {
            Log.w(TAG, "debug save failed", t);
        }
    }

    public synchronized void stop() {
        stopRequested = true;
    }

    public synchronized void cancel() {
        stopRequested = true;
        if (thread != null && thread.isAlive()) {
            try {
                thread.join(300);
            } catch (InterruptedException ignored) {
            }
        }
        releaseRecord();
    }

    public synchronized void release() {
        cancel();
    }

    private void releaseRecord() {
        if (record != null) {
            try {
                record.release();
            } catch (Throwable ignored) {
            }
            record = null;
        }
    }

    public String getLastError() {
        return lastError;
    }
}
