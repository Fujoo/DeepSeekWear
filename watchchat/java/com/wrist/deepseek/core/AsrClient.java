package com.wrist.deepseek.core;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * OpenAI-compatible /audio/transcriptions client.
 * Defaults to SiliconFlow TeleSpeechASR, which is fast, good at Chinese and
 * noticeably more robust on quiet/noisy watch-microphone audio than Whisper.
 */
public class AsrClient {
    public interface Callback {
        void onResult(String text);

        void onError(String message);
    }

    public void transcribe(Settings settings, byte[] wav, Callback callback) {
        String endpoint = settings.getAsrEndpoint();
        String key = settings.getAsrKey();
        if (TextUtils.isEmpty(endpoint)) {
            callback.onError("还没有配置语音识别服务");
            return;
        }
        HttpURLConnection c = null;
        try {
            String boundary = "----DSW" + UUID.randomUUID().toString().replace("-", "");
            byte[] body = multipart(boundary, settings.getAsrModel(), wav);

            c = Http.open(endpoint, 15000, 180000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(body.length);
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            c.setRequestProperty("Accept", "application/json");
            if (!TextUtils.isEmpty(key)) {
                c.setRequestProperty("Authorization", "Bearer " + key);
            }
            try (OutputStream os = c.getOutputStream()) {
                os.write(body);
                os.flush();
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String raw = Http.readAll(in);
            if (code != 200) {
                callback.onError(asrError(code, raw));
                return;
            }
            String text = parseText(raw);
            if (TextUtils.isEmpty(text)) {
                callback.onError("没有听清，再说一次吧");
                return;
            }
            callback.onResult(text);
        } catch (Throwable t) {
            callback.onError(DeepSeekClient.networkMessage(t));
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static byte[] multipart(String boundary, String model, byte[] wav) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(wav.length + 1024);
        String start = "--" + boundary + "\r\n";
        write(out, start);
        write(out, "Content-Disposition: form-data; name=\"model\"\r\n\r\n");
        write(out, model == null ? "" : model);
        write(out, "\r\n");
        write(out, "--" + boundary + "\r\n");
        write(out, "Content-Disposition: form-data; name=\"response_format\"\r\n\r\njson\r\n");
        write(out, "--" + boundary + "\r\n");
        write(out, "Content-Disposition: form-data; name=\"file\"; filename=\"voice.wav\"\r\n");
        write(out, "Content-Type: audio/wav\r\n\r\n");
        out.write(wav);
        write(out, "\r\n--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String s) throws Exception {
        out.write(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String parseText(String raw) {
        String text = null;
        try {
            JSONObject o = new JSONObject(raw);
            text = o.optString("text", "");
            if (TextUtils.isEmpty(text)) {
                Object r = o.opt("result");
                if (r instanceof JSONArray && ((JSONArray) r).length() > 0) {
                    text = ((JSONArray) r).optString(0, "");
                } else if (r != null) {
                    text = String.valueOf(r);
                }
            }
        } catch (Throwable t) {
            text = raw;
        }
        if (text == null) return "";
        text = clean(text);
        return text;
    }

    public static String clean(String text) {
        if (text == null) return "";
        String s = text;
        s = s.replace("\\n", " ").replace("\\r", " ");
        s = s.replaceAll("<\\|[^|>]*\\|>", " ");
        s = s.replaceAll("\\[[A-Za-z]{2,8}\\]", " ");
        s = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ");
        s = s.replaceAll("^[\"'“”‘’`]+", "").replaceAll("[\"'“”‘’`]+$", "");
        s = s.replaceAll("\\s+", " ").trim();
        if ("null".equalsIgnoreCase(s) || "[BLANK_AUDIO]".equalsIgnoreCase(s)
                || "(silence)".equalsIgnoreCase(s)) return "";
        return s.trim();
    }

    private static String asrError(int code, String raw) {
        String detail = "";
        try {
            JSONObject o = new JSONObject(raw);
            detail = o.optString("message", "");
            if (detail.isEmpty() && o.optJSONObject("error") != null) {
                detail = o.optJSONObject("error").optString("message", "");
            }
        } catch (Throwable ignored) {
        }
        switch (code) {
            case 401:
            case 403:
                return "语音识别 Key 无效或权限不足";
            case 404:
                return "语音识别接口不存在，请检查设置";
            case 413:
                return "录音太长，请说短一点";
            case 429:
                return "语音识别请求太频繁，请稍后再试";
            default:
                return detail.isEmpty() ? ("语音识别失败（HTTP " + code + "）") : detail;
        }
    }
}
