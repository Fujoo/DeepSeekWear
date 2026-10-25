package com.wrist.deepseek.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.wrist.deepseek.core.Settings;
import com.wrist.deepseek.util.Haptics;
import com.wrist.deepseek.util.Ui;

import java.util.ArrayList;
import java.util.List;

/** Four-digit PIN pad. No soft keyboard, no plaintext PIN storage. */
public class PinDialog {
    public interface OnSuccess {
        void onSuccess();
    }

    private final Activity activity;
    private final Settings settings;
    private final boolean setupMode;
    private final OnSuccess success;
    private final Dialog dialog;
    private final TextView title;
    private final TextView subtitle;
    private final TextView error;
    private final List<View> dots = new ArrayList<>();
    private final StringBuilder entered = new StringBuilder();
    private String firstPin;
    private boolean busy;

    private PinDialog(Activity a, Settings settings, boolean setupMode, OnSuccess success) {
        this.activity = a;
        this.settings = settings;
        this.setupMode = setupMode;
        this.success = success;
        this.dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);

        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.round(0xFF17171D, 22, a));
        panel.setPadding(Ui.dp(a, 16), Ui.dp(a, 16), Ui.dp(a, 16), Ui.dp(a, 12));

        title = Ui.mediumText(a, setupMode ? "设置隐私密码" : "输入隐私密码", 18, Ui.TEXT);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);

        subtitle = Ui.text(a, setupMode ? "四位数字，用于进入隐私空间" : "四位数字，确认后进入", 11, Ui.MUTED);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(a, 5);
        panel.addView(subtitle, slp);

        LinearLayout dotRow = new LinearLayout(a);
        dotRow.setOrientation(LinearLayout.HORIZONTAL);
        dotRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = Ui.dp(a, 14);
        panel.addView(dotRow, dlp);
        for (int i = 0; i < 4; i++) {
            View dot = new View(a);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 13), Ui.dp(a, 13));
            lp.leftMargin = Ui.dp(a, 5);
            lp.rightMargin = Ui.dp(a, 5);
            dotRow.addView(dot, lp);
            dots.add(dot);
        }

        error = Ui.text(a, "", 11, Ui.ERROR);
        error.setGravity(Gravity.CENTER);
        error.setVisibility(View.GONE);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.topMargin = Ui.dp(a, 7);
        panel.addView(error, elp);

        LinearLayout pad = new LinearLayout(a);
        pad.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = Ui.dp(a, 8);
        panel.addView(pad, plp);

        String[][] rows = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"清空", "0", "⌫"}};
        for (String[] row : rows) {
            LinearLayout line = new LinearLayout(a);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER);
            for (String label : row) {
                TextView key = key(label);
                if ("清空".equals(label)) key.setTextSize(11);
                if ("⌫".equals(label)) key.setTextSize(18);
                key.setOnClickListener(v -> {
                    Haptics.crownTick(v);
                    onKey(label);
                });
                LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                        0, Ui.dp(a, 46), 1);
                klp.setMargins(Ui.dp(a, 3), Ui.dp(a, 3), Ui.dp(a, 3), Ui.dp(a, 3));
                line.addView(key, klp);
            }
            pad.addView(line, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        TextView cancel = Ui.mediumText(a, "取消", 13, Ui.MUTED);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(Ui.ripple(0x22FFFFFF, 12, a));
        cancel.setClickable(true);
        cancel.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 38));
        clp.topMargin = Ui.dp(a, 8);
        panel.addView(cancel, clp);

        updateDots();
        dialog.setContentView(panel);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.70f);
            w.setGravity(Gravity.CENTER);
            int maxW = a.getResources().getDisplayMetrics().widthPixels - Ui.dp(a, 20);
            w.setLayout(Math.min(Ui.dp(a, 292), maxW), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    public static void show(Activity a, Settings settings, boolean setupMode, OnSuccess success) {
        PinDialog d = new PinDialog(a, settings, setupMode, success);
        d.dialog.show();
        Window w = d.dialog.getWindow();
        if (w != null) {
            int maxW = a.getResources().getDisplayMetrics().widthPixels - Ui.dp(a, 20);
            w.setLayout(Math.min(Ui.dp(a, 292), maxW), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private TextView key(String label) {
        TextView t = Ui.mediumText(activity, label, 20, Ui.TEXT);
        t.setGravity(Gravity.CENTER);
        GradientDrawable content = Ui.round(0xFF202028, 12, activity);
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content,
                Ui.round(Color.WHITE, 12, activity)));
        t.setClickable(true);
        return t;
    }

    private void onKey(String label) {
        if (busy) return;
        error.setVisibility(View.GONE);
        if ("清空".equals(label)) {
            entered.setLength(0);
            updateDots();
            return;
        }
        if ("⌫".equals(label)) {
            if (entered.length() > 0) entered.deleteCharAt(entered.length() - 1);
            updateDots();
            return;
        }
        if (entered.length() >= 4) return;
        entered.append(label);
        updateDots();
        if (entered.length() == 4) {
            busy = true;
            activity.getWindow().getDecorView().postDelayed(this::finishEntry, 170);
        }
    }

    private void finishEntry() {
        String pin = entered.toString();
        entered.setLength(0);
        busy = false;
        if (setupMode) {
            if (firstPin == null) {
                firstPin = pin;
                Haptics.success(activity);
                title.setText("再输一次");
                subtitle.setText("确认四位密码");
                updateDots();
                return;
            }
            if (!firstPin.equals(pin)) {
                firstPin = null;
                title.setText("设置隐私密码");
                subtitle.setText("两次不一致，请重新设置");
                error.setText("两次输入不一致");
                error.setVisibility(View.VISIBLE);
                Haptics.error(activity);
                updateDots();
                return;
            }
            settings.setPrivacyPin(pin);
            dialog.dismiss();
            Haptics.success(activity);
            if (success != null) success.onSuccess();
        } else {
            if (settings.verifyPrivacyPin(pin)) {
                dialog.dismiss();
                Haptics.success(activity);
                if (success != null) success.onSuccess();
            } else {
                error.setText("密码错误，请重试");
                error.setVisibility(View.VISIBLE);
                Haptics.error(activity);
                updateDots();
            }
        }
    }

    private void updateDots() {
        for (int i = 0; i < dots.size(); i++) {
            boolean on = i < entered.length();
            dots.get(i).setBackground(on
                    ? Ui.round(Ui.ACCENT, 6.5f, activity)
                    : Ui.roundStroke(0xFF15151B, 6.5f, 0xFF4A4A57, 1, activity));
        }
    }
}
