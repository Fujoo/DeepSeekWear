package com.wrist.deepseek.util;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

/** Small, crisp haptics tuned for watch linear motors. */
public final class Haptics {
    private Haptics() {
    }

    public static void tick(Context c) {
        pulse(c, 6, 80);
    }

    public static void soft(Context c) {
        pulse(c, 5, 50);
    }

    /**
     * Prefer the platform keyboard haptic. On some watches the app-level
     * Vibrator is filtered by a “mute/haptic” policy, while View haptics still
     * reach the linear motor. FLAG_IGNORE_GLOBAL_SETTING also covers the case
     * where system haptic feedback was turned off.
     */
    /**
     * A crown-detent style tick: short, dry and granular, like one notch of a
     * rotating watch crown. CLOCK_TICK is the platform's own detent effect.
     */
    public static void crownTick(View view) {
        if (view == null) return;
        try {
            boolean handled = view.performHapticFeedback(
                    HapticFeedbackConstants.CLOCK_TICK,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            if (!handled) pulse(view.getContext(), 4, 140);
        } catch (Throwable t) {
            pulse(view.getContext(), 4, 140);
        }
    }

    public static void success(Context c) {
        try {
            Vibrator v = vibrator(c);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 29 && hasAmplitude(v)) {
                long[] pattern = {0, 10, 38, 10};
                int[] amps = {0, 90, 0, 110};
                v.vibrate(VibrationEffect.createWaveform(pattern, amps, -1));
            } else if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(18, 100));
            } else {
                v.vibrate(18);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void error(Context c) {
        try {
            Vibrator v = vibrator(c);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 29 && hasAmplitude(v)) {
                long[] pattern = {0, 14, 34, 14};
                int[] amps = {0, 150, 0, 170};
                v.vibrate(VibrationEffect.createWaveform(pattern, amps, -1));
            } else if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(38, 160));
            } else {
                v.vibrate(38);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void pulse(Context c, int ms, int amp) {
        try {
            Vibrator v = vibrator(c);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26 && hasAmplitude(v)) {
                v.vibrate(VibrationEffect.createWaveform(new long[]{0, ms}, new int[]{0, amp}, -1));
            } else if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(ms);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Vibrator vibrator(Context c) {
        Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        return v != null && v.hasVibrator() ? v : null;
    }

    private static boolean hasAmplitude(Vibrator v) {
        try {
            return v.hasAmplitudeControl();
        } catch (Throwable t) {
            return false;
        }
    }
}
