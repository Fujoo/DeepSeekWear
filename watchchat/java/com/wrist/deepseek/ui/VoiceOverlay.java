package com.wrist.deepseek.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.wrist.deepseek.util.Ui;

/** Full-screen voice sheet. It owns no recording logic, only presentation. */
public class VoiceOverlay extends FrameLayout {
    public interface Listener {
        void onStop();

        void onCancel();

        void onRetry();

        void onWhisperChanged(boolean whisper);
    }

    private final Listener listener;
    private final VoiceOrbView orb;
    private final TextView title;
    private final TextView status;
    private final TextView subtitle;
    private final TextView timer;
    private final TextView result;
    private final TextView whisperPill;
    private final FrameLayout stopButton;
    private final ProgressBar progress;
    private final LinearLayout errorActions;
    private final TextView hint;
    private boolean whisper;
    private boolean showing;
    private boolean recording;

    public VoiceOverlay(Context c, Listener listener) {
        super(c);
        this.listener = listener;
        setBackgroundColor(0xFC08080A);
        setClickable(true);
        setFocusable(true);
        setVisibility(GONE);

        LinearLayout column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 12));
        addView(column, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 42)));

        IconView close = new IconView(c, IconView.CLOSE, Ui.MUTED, 30);
        close.setBackground(Ui.ripple(0x22FFFFFF, 15, c));
        close.setOnClickListener(v -> {
            if (listener != null) listener.onCancel();
        });
        top.addView(close);

        title = Ui.mediumText(c, "语音输入", 15, Ui.TEXT);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tlp.leftMargin = Ui.dp(c, 8);
        top.addView(title, tlp);

        whisperPill = Ui.mediumText(c, "悄悄话", 11, Ui.MUTED);
        whisperPill.setGravity(Gravity.CENTER);
        whisperPill.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 10), 0);
        whisperPill.setBackground(Ui.roundStroke(0xFF17171D, 14, Ui.BORDER, 1, c));
        whisperPill.setClickable(true);
        whisperPill.setOnClickListener(v -> {
            whisper = !whisper;
            updateWhisperPill();
            if (listener != null) listener.onWhisperChanged(whisper);
        });
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(c, 30));
        top.addView(whisperPill, wlp);

        LinearLayout center = new LinearLayout(c);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_HORIZONTAL);
        column.addView(center, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        orb = new VoiceOrbView(c, 164);
        center.addView(orb);

        status = Ui.mediumText(c, "正在聆听", 19, Ui.TEXT);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(c, 2);
        center.addView(status, slp);

        subtitle = Ui.text(c, "轻声说也能听清", 12, Ui.MUTED);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sub = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sub.topMargin = Ui.dp(c, 5);
        center.addView(subtitle, sub);

        timer = Ui.text(c, "00:00", 11, Ui.BLUE_LIGHT);
        timer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tml = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tml.topMargin = Ui.dp(c, 4);
        center.addView(timer, tml);

        result = Ui.text(c, "", 13, Ui.TEXT);
        result.setGravity(Gravity.CENTER);
        result.setVisibility(GONE);
        result.setMaxLines(4);
        result.setPadding(Ui.dp(c, 12), Ui.dp(c, 9), Ui.dp(c, 12), Ui.dp(c, 9));
        result.setBackground(Ui.roundStroke(0xFF17171D, 12, Ui.BORDER, 1, c));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dp(c, 10);
        center.addView(result, rlp);

        errorActions = new LinearLayout(c);
        errorActions.setOrientation(LinearLayout.HORIZONTAL);
        errorActions.setGravity(Gravity.CENTER);
        errorActions.setVisibility(GONE);
        TextView retry = Ui.mediumText(c, "重试", 13, Ui.BLUE_LIGHT);
        retry.setGravity(Gravity.CENTER);
        retry.setBackground(Ui.ripple(0x334D6BFE, 11, c));
        retry.setClickable(true);
        retry.setOnClickListener(v -> {
            if (listener != null) listener.onRetry();
        });
        errorActions.addView(retry, new LinearLayout.LayoutParams(Ui.dp(c, 76), Ui.dp(c, 36)));
        TextView cancel = Ui.mediumText(c, "关闭", 13, Ui.MUTED);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(Ui.ripple(0x22FFFFFF, 11, c));
        cancel.setClickable(true);
        cancel.setOnClickListener(v -> {
            if (listener != null) listener.onCancel();
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Ui.dp(c, 76), Ui.dp(c, 36));
        clp.leftMargin = Ui.dp(c, 6);
        errorActions.addView(cancel, clp);
        LinearLayout.LayoutParams ealp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ealp.topMargin = Ui.dp(c, 8);
        center.addView(errorActions, ealp);

        LinearLayout bottom = new LinearLayout(c);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        column.addView(bottom, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout stopWrap = new FrameLayout(c);
        GradientDrawable stopBg = Ui.round(0xFFF2F2F7, 34, c);
        stopWrap.setBackground(stopBg);
        stopButton = stopWrap;
        stopButton.setClickable(true);
        IconView stopIcon = new IconView(c, IconView.STOP, 0xFF111114, 24);
        stopIcon.setClickable(false);
        FrameLayout.LayoutParams silp = new FrameLayout.LayoutParams(
                Ui.dp(c, 24), Ui.dp(c, 24));
        silp.gravity = Gravity.CENTER;
        stopButton.addView(stopIcon, silp);
        stopButton.setOnClickListener(v -> {
            if (listener != null) listener.onStop();
        });
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(Ui.dp(c, 64), Ui.dp(c, 64));
        stopLp.topMargin = Ui.dp(c, 4);
        bottom.addView(stopButton, stopLp);

        progress = new ProgressBar(c);
        progress.setIndeterminate(true);
        progress.setVisibility(GONE);
        try {
            progress.getIndeterminateDrawable().setColorFilter(Ui.BLUE_LIGHT, PorterDuff.Mode.SRC_IN);
        } catch (Throwable ignored) {
        }
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(Ui.dp(c, 34), Ui.dp(c, 34));
        plp.topMargin = Ui.dp(c, 8);
        bottom.addView(progress, plp);

        hint = Ui.text(c, "点一下结束，也可以停顿自动结束", 11, Ui.DIM);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = Ui.dp(c, 7);
        bottom.addView(hint, hlp);

        setKeepScreenOn(true);
    }

    public void show(boolean whisperMode) {
        whisper = whisperMode;
        updateWhisperPill();
        showing = true;
        recording = true;
        setVisibility(VISIBLE);
        setAlpha(0f);
        animate().alpha(1f).setDuration(150).start();
        showRecording();
    }

    public void hide() {
        showing = false;
        recording = false;
        animate().alpha(0f).setDuration(120).withEndAction(() -> {
            setVisibility(GONE);
            orb.clearLevel();
        }).start();
    }

    public boolean isShowing() {
        return showing;
    }

    public boolean isRecording() {
        return recording;
    }

    private void showRecording() {
        recording = true;
        orb.setState(VoiceOrbView.RECORDING);
        orb.clearLevel();
        status.setText("正在聆听");
        status.setTextColor(Ui.TEXT);
        subtitle.setText(whisper ? "靠近手表，用最小的声音说" : "轻声说也能听清");
        subtitle.setVisibility(VISIBLE);
        timer.setVisibility(VISIBLE);
        timer.setText("00:00");
        result.setVisibility(GONE);
        errorActions.setVisibility(GONE);
        stopButton.setVisibility(VISIBLE);
        progress.setVisibility(GONE);
        hint.setVisibility(VISIBLE);
        hint.setText("点一下结束，也可以停顿自动结束");
    }

    public void showTranscribing() {
        recording = false;
        orb.setState(VoiceOrbView.THINKING);
        status.setText("正在识别");
        status.setTextColor(Ui.TEXT);
        subtitle.setText("结合上下文，整理成你真正想说的话");
        subtitle.setVisibility(VISIBLE);
        timer.setVisibility(GONE);
        result.setVisibility(GONE);
        errorActions.setVisibility(GONE);
        stopButton.setVisibility(GONE);
        progress.setVisibility(VISIBLE);
        hint.setText("通常只需一两秒");
    }

    public void showError(String message) {
        recording = false;
        orb.setState(VoiceOrbView.ERROR);
        status.setText("没有听清");
        status.setTextColor(Ui.ERROR);
        subtitle.setVisibility(GONE);
        timer.setVisibility(GONE);
        result.setText(message == null ? "请再试一次" : message);
        result.setTextColor(Ui.MUTED);
        result.setVisibility(VISIBLE);
        errorActions.setVisibility(VISIBLE);
        stopButton.setVisibility(GONE);
        progress.setVisibility(GONE);
        hint.setText("把手表靠近嘴边，轻声说");
            }

    public void setLevel(float level) {
        if (recording) orb.setLevel(level);
    }

    public void setTimer(long ms) {
        if (!recording) return;
        long total = ms / 1000;
        timer.setText(String.format(java.util.Locale.CHINA, "%02d:%02d", total / 60, total % 60));
    }

    private void updateWhisperPill() {
        whisperPill.setText(whisper ? "悄悄话 · 开" : "悄悄话");
        whisperPill.setTextColor(whisper ? Ui.TEXT : Ui.MUTED);
        whisperPill.setBackground(whisper
                ? Ui.round(0xFF3448C9, 14, getContext())
                : Ui.roundStroke(0xFF17171D, 14, Ui.BORDER, 1, getContext()));
    }

    public boolean isWhisper() {
        return whisper;
    }

    public void setWhisper(boolean value) {
        whisper = value;
        updateWhisperPill();
    }
}
