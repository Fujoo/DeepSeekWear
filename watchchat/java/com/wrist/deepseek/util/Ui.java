package com.wrist.deepseek.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Ui {
    public static final int BG = 0xFF08080A;
    public static final int SURFACE = 0xFF131317;
    public static final int SURFACE2 = 0xFF1B1B22;
    public static final int SURFACE3 = 0xFF25252E;
    public static final int BORDER = 0xFF2C2C35;
    public static final int TEXT = 0xFFF4F4F7;
    public static final int MUTED = 0xFF9A9AA6;
    public static final int DIM = 0xFF6C6C78;
    public static final int ACCENT = 0xFF4D6BFE;
    public static final int ACCENT_DARK = 0xFF2F49D8;
    public static final int USER_BUBBLE = 0xFF3A55E8;
    public static final int BLUE_LIGHT = 0xFF97A9FF;
    public static final int ERROR = 0xFFFF7373;
    public static final int CODE_BG = 0xFF1C1C24;

    private static Typeface regular;
    private static Typeface medium;
    private static Typeface mono;

    private Ui() {
    }

    public static int dp(Context c, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                c.getResources().getDisplayMetrics()));
    }

    public static int sp(Context c, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                c.getResources().getDisplayMetrics()));
    }

    public static Typeface regular() {
        if (regular == null) regular = Typeface.create("sans-serif", Typeface.NORMAL);
        return regular;
    }

    public static Typeface medium() {
        if (medium == null) medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        return medium;
    }

    public static Typeface mono() {
        if (mono == null) mono = Typeface.MONOSPACE;
        return mono;
    }

    public static GradientDrawable round(int color, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable roundStroke(int color, float radiusDp, int strokeColor, float strokeDp, Context c) {
        GradientDrawable d = round(color, radiusDp, c);
        d.setStroke(Math.max(1, dp(c, strokeDp)), strokeColor);
        return d;
    }

    public static GradientDrawable bubble(int color, Context c, boolean user) {
        float r = dp(c, 19);
        float small = dp(c, 6);
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        if (user) {
            d.setCornerRadii(new float[]{r, r, r, r, r, r, small, small});
        } else {
            d.setCornerRadii(new float[]{r, r, r, r, small, small, r, r});
        }
        return d;
    }

    public static RippleDrawable ripple(int color, float radiusDp, Context c) {
        GradientDrawable mask = round(Color.WHITE, radiusDp, c);
        return new RippleDrawable(ColorStateList.valueOf(color), null, mask);
    }

    public static TextView text(Context c, CharSequence value, float sizeSp, int color) {
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextSize(sizeSp);
        v.setTextColor(color);
        v.setTypeface(regular());
        v.setIncludeFontPadding(false);
        v.setLineSpacing(dp(c, 3), 1.0f);
        return v;
    }

    public static TextView mediumText(Context c, CharSequence value, float sizeSp, int color) {
        TextView v = text(c, value, sizeSp, color);
        v.setTypeface(medium());
        return v;
    }

    public static void setMargins(View v, int left, int top, int right, int bottom) {
        if (v.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams lp =
                    (android.view.ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.setMargins(left, top, right, bottom);
            v.setLayoutParams(lp);
        }
    }

    public static void toast(Context c, String message) {
        if (TextUtils.isEmpty(message)) return;
        Toast.makeText(c.getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    public static void copy(Context c, String text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("DeepSeek", text));
            toast(c, "已复制");
        }
    }

    public static void haptic(Context c, int ms) {
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(ms);
            }
        } catch (Throwable ignored) {
        }
    }

    public static String time(long ts) {
        if (ts <= 0) return "";
        Date d = new Date(ts);
        Date now = new Date();
        SimpleDateFormat sameDay = new SimpleDateFormat("HH:mm", Locale.CHINA);
        if (d.getYear() == now.getYear() && d.getMonth() == now.getMonth()
                && d.getDate() == now.getDate()) {
            return sameDay.format(d);
        }
        return new SimpleDateFormat("M月d日", Locale.CHINA).format(d);
    }

    public static String relative(long ts) {
        long delta = Math.max(0, System.currentTimeMillis() - ts);
        if (delta < 60_000) return "刚刚";
        if (delta < 3600_000) return (delta / 60_000) + " 分钟前";
        if (delta < 24 * 3600_000L) return (delta / 3600_000) + " 小时前";
        if (delta < 7 * 24 * 3600_000L) return (delta / (24 * 3600_000L)) + " 天前";
        return new SimpleDateFormat("M月d日", Locale.CHINA).format(new Date(ts));
    }

    public static String titleFrom(String text) {
        if (text == null) return "新对话";
        String s = text.replace('\n', ' ').replace('\r', ' ').trim();
        s = s.replaceAll("\\s+", " ");
        if (s.isEmpty()) return "新对话";
        if (s.length() > 20) s = s.substring(0, 20) + "…";
        return s;
    }

    public static String oneLine(String text, int max) {
        if (text == null) return "";
        String s = stripMarkdown(text).replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (s.length() > max) s = s.substring(0, max) + "…";
        return s;
    }

    public static String stripMarkdown(String md) {
        if (md == null) return "";
        String s = md;
        s = s.replaceAll("```[a-zA-Z0-9_+-]*\\n?", "");
        s = s.replace("```", "");
        s = s.replaceAll("`([^`]*)`", "$1");
        s = s.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
        s = s.replaceAll("__([^_]+)__", "$1");
        s = s.replaceAll("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)", "$1");
        s = s.replaceAll("~~([^~]+)~~", "$1");
        s = s.replaceAll("(?m)^#{1,6}\\s*", "");
        s = s.replaceAll("(?m)^>\\s?", "");
        s = s.replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1");
        s = s.replaceAll("(?m)^\\s*[-*+]\\s+", "");
        s = s.replaceAll("(?m)^\\s*\\d+\\.\\s+", "");
        s = s.replaceAll("\\n{3,}", "\\n\\n");
        return s.trim();
    }

    public static String keyHint(String key) {
        if (TextUtils.isEmpty(key)) return "未设置";
        if (key.length() <= 8) return "已设置";
        return "已设置 · " + key.substring(key.length() - 4);
    }
}
