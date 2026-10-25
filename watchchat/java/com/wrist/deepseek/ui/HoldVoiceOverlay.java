package com.wrist.deepseek.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.wrist.deepseek.util.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact press-and-hold voice surface. Deliberately different from the
 * full-screen mic sheet: it sits above the composer and says "松手发送".
 */
public class HoldVoiceOverlay extends FrameLayout {
    private final View scrim;
    private final LinearLayout card;
    private final TextView title;
    private final TextView subtitle;
    private final TextView timer;
    private final TextView hint;
    private final TextView fastBadge;
    private final WaveBars wave;
    private boolean recording;
    private boolean showing;
    private boolean cancelArmed;
    private boolean fast;
    private boolean whisper;

    public HoldVoiceOverlay(Context c) {
        super(c);
        setVisibility(GONE);
        setClickable(true);
        setFocusable(true);

        scrim = new View(c);
        scrim.setBackgroundColor(0x40000000);
        addView(scrim, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 11));
        card.setBackground(Ui.roundStroke(0xF2131319, 22, 0xFF30303B, 1, c));
        card.setElevation(Ui.dp(c, 10));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.BOTTOM;
        clp.leftMargin = Ui.dp(c, 14);
        clp.rightMargin = Ui.dp(c, 14);
        clp.bottomMargin = Ui.dp(c, 82);
        addView(card, clp);

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top);

        FrameLayout mic = new FrameLayout(c);
        mic.setBackground(Ui.round(Ui.ACCENT, 15, c));
        IconView micIcon = new IconView(c, IconView.MIC, Color.WHITE, 17);
        micIcon.setClickable(false);
        FrameLayout.LayoutParams milp = new FrameLayout.LayoutParams(Ui.dp(c, 17), Ui.dp(c, 17));
        milp.gravity = Gravity.CENTER;
        mic.addView(micIcon, milp);
        top.addView(mic, new LinearLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30)));

        LinearLayout textBox = new LinearLayout(c);
        textBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tblp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tblp.leftMargin = Ui.dp(c, 9);
        top.addView(textBox, tblp);

        title = Ui.mediumText(c, "松手发送", 16, Ui.TEXT);
        textBox.addView(title);
        subtitle = Ui.text(c, "正在聆听", 10.5f, Ui.MUTED);
        subtitle.setPadding(0, Ui.dp(c, 2), 0, 0);
        textBox.addView(subtitle);

        fastBadge = Ui.mediumText(c, "极速", 9, Ui.BLUE_LIGHT);
        fastBadge.setGravity(Gravity.CENTER);
        fastBadge.setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 6), 0);
        fastBadge.setBackground(Ui.round(0xFF202C5A, 7, c));
        fastBadge.setVisibility(GONE);
        LinearLayout.LayoutParams fblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(c, 18));
        fblp.rightMargin = Ui.dp(c, 7);
        top.addView(fastBadge, fblp);

        timer = Ui.text(c, "00:00", 10.5f, Ui.BLUE_LIGHT);
        timer.setGravity(Gravity.END);
        top.addView(timer);

        wave = new WaveBars(c);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 30));
        wlp.topMargin = Ui.dp(c, 8);
        card.addView(wave, wlp);

        hint = Ui.text(c, "轻声说，松手就发送", 10, Ui.DIM);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = Ui.dp(c, 3);
        card.addView(hint, hlp);

        setKeepScreenOn(true);
    }

    public void show(boolean whisper, boolean fast) {
        showing = true;
        recording = true;
        cancelArmed = false;
        this.fast = fast;
        this.whisper = whisper;
        card.setBackground(Ui.roundStroke(0xF2131319, 22, 0xFF30303B, 1, getContext()));
        setVisibility(VISIBLE);
        title.setText("松手发送");
        title.setTextColor(Ui.TEXT);
        subtitle.setText(whisper ? "正在聆听 · 悄悄话" : "正在聆听");
        subtitle.setTextColor(Ui.MUTED);
        fastBadge.setVisibility(fast ? VISIBLE : GONE);
        timer.setText("00:00");
        timer.setVisibility(VISIBLE);
        hint.setText(fast ? "上滑取消 · 极速，松手发送" : "上滑取消 · 松手发送");
        wave.setActive(true);
        wave.setLevel(0);
        card.animate().cancel();
        card.setTranslationY(Ui.dp(getContext(), 16));
        card.setAlpha(0f);
        card.animate().translationY(0).alpha(1f).setDuration(180).start();
    }

    public void hide() {
        showing = false;
        recording = false;
        card.animate().cancel();
        card.animate().translationY(Ui.dp(getContext(), 12)).alpha(0f)
                .setDuration(130).withEndAction(() -> {
                    setVisibility(GONE);
                    wave.setActive(false);
                }).start();
    }

    public boolean isShowing() {
        return showing;
    }

    public void setCancelArmed(boolean armed) {
        if (!recording) return;
        cancelArmed = armed;
        if (armed) {
            title.setText("松手取消");
            title.setTextColor(Ui.ERROR);
            subtitle.setText("已上滑，松手不会发送");
            subtitle.setTextColor(0xFFFFA3A3);
            hint.setText("松手丢弃这段语音");
            card.setBackground(Ui.roundStroke(0xF2131319, 22, 0xFF6A3232, 1, getContext()));
        } else {
            title.setText("松手发送");
            title.setTextColor(Ui.TEXT);
            subtitle.setText(whisper ? "正在聆听 · 悄悄话" : "正在聆听");
            subtitle.setTextColor(Ui.MUTED);
            hint.setText(fast ? "上滑取消 · 极速，松手发送" : "上滑取消 · 松手发送");
            card.setBackground(Ui.roundStroke(0xF2131319, 22, 0xFF30303B, 1, getContext()));
        }
    }

    public void setLevel(float level) {
        if (recording) wave.setLevel(level);
    }

    public void setTimer(long ms) {
        if (!recording) return;
        long s = ms / 1000;
        timer.setText(String.format(java.util.Locale.CHINA, "%02d:%02d", s / 60, s % 60));
    }

    public void showTranscribing() {
        recording = false;
        title.setText("正在识别");
        title.setTextColor(Ui.TEXT);
        subtitle.setText("识别完自动发送");
        subtitle.setTextColor(Ui.MUTED);
        timer.setText("…");
        fastBadge.setVisibility(GONE);
        cancelArmed = false;
        card.setBackground(Ui.roundStroke(0xF2131319, 22, 0xFF30303B, 1, getContext()));
        hint.setText("马上发送");
        wave.setActive(false);
    }

    public void showError(String message) {
        recording = false;
        title.setText("没听清");
        title.setTextColor(Ui.ERROR);
        subtitle.setText(message == null ? "再说一次吧" : message);
        subtitle.setTextColor(Ui.MUTED);
        timer.setText("");
        fastBadge.setVisibility(GONE);
        hint.setText("松开输入框后重试");
        wave.setActive(false);
        postDelayed(this::hide, 2400);
    }

    /** Small, premium waveform; only animates while the overlay is active. */
    private static class WaveBars extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Float> history = new ArrayList<>();
        private float level;
        private boolean active;

        WaveBars(Context c) {
            super(c);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        void setLevel(float v) {
            level = Math.max(0, Math.min(1, v));
            history.add(level);
            while (history.size() > 30) history.remove(0);
            invalidate();
        }

        void setActive(boolean value) {
            active = value;
            if (value) postInvalidateOnAnimation();
            else invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;
            int count = 25;
            float gap = w / (count + 2f);
            float cy = h / 2f;
            long now = SystemClock.uptimeMillis();
            for (int i = 0; i < count; i++) {
                float base = 0.16f + 0.16f * (float) Math.abs(Math.sin(now / 340f + i * 0.55f));
                int idx = history.size() - count + i;
                if (idx >= 0) base = Math.max(base, history.get(idx));
                if (!active) base *= 0.35f;
                float bh = Ui.dp(getContext(), 2.2f) + base * Ui.dp(getContext(), 17f);
                float x = gap * (i + 1.35f);
                RectF bar = new RectF(x - Ui.dp(getContext(), 1.2f), cy - bh / 2,
                        x + Ui.dp(getContext(), 1.2f), cy + bh / 2);
                paint.setShader(new LinearGradient(0, cy - bh / 2, 0, cy + bh / 2,
                        new int[]{0xFF8296FF, 0xFF4D6BFE}, null, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(bar, Ui.dp(getContext(), 1.3f),
                        Ui.dp(getContext(), 1.3f), paint);
            }
            paint.setShader(null);
            if (active) postInvalidateOnAnimation();
        }
    }
}
