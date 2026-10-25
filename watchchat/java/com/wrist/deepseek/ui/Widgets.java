package com.wrist.deepseek.ui;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wrist.deepseek.util.Ui;

import java.util.ArrayList;
import java.util.List;

public final class Widgets {
    public interface OnText {
        void onText(String text);
    }

    public interface OnItem {
        void onItem(int index);
    }

    public interface OnConfirm {
        void onConfirm();
    }

    public static class Item {
        public final String label;
        public final int icon;
        public final int color;
        public final boolean danger;

        public Item(String label, int icon) {
            this(label, icon, Ui.TEXT, false);
        }

        public Item(String label, int icon, int color, boolean danger) {
            this.label = label;
            this.icon = icon;
            this.color = color;
            this.danger = danger;
        }
    }

    private Widgets() {
    }

    public static void showSheet(Activity a, String title, List<Item> items, OnItem callback) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF17171D);
        bg.setCornerRadii(new float[]{Ui.dp(a, 20), Ui.dp(a, 20), Ui.dp(a, 20), Ui.dp(a, 20), 0, 0, 0, 0});
        panel.setBackground(bg);
        panel.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 12));

        View handle = new View(a);
        GradientDrawable hb = Ui.round(0xFF3C3C48, 2, a);
        handle.setBackground(hb);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(Ui.dp(a, 30), Ui.dp(a, 4));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.topMargin = Ui.dp(a, 2);
        hlp.bottomMargin = Ui.dp(a, 8);
        panel.addView(handle, hlp);

        if (!TextUtils.isEmpty(title)) {
            TextView tv = Ui.mediumText(a, title, 14, Ui.MUTED);
            tv.setPadding(Ui.dp(a, 10), Ui.dp(a, 2), Ui.dp(a, 10), Ui.dp(a, 8));
            panel.addView(tv);
        }

        Window w = d.getWindow();

        ScrollView scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            Item item = items.get(i);
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 12), 0);
            row.setBackground(Ui.ripple(0x22FFFFFF, 12, a));
            row.setClickable(true);
            IconView icon = new IconView(a, item.icon, item.danger ? Ui.ERROR : Ui.MUTED, 21);
            row.addView(icon);
            TextView label = Ui.text(a, item.label, 15, item.danger ? Ui.ERROR : item.color);
            label.setPadding(Ui.dp(a, 13), 0, 0, 0);
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.setOnClickListener(v -> dismissSheet(d, panel, w, callback, index));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 48));
            rlp.setMargins(0, Ui.dp(a, 1), 0, Ui.dp(a, 1));
            list.addView(row, rlp);
        }
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        d.setContentView(panel);
        panel.setAlpha(0f);
        panel.setTranslationY(Ui.dp(a, 28));
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0f);
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.show();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            animateDim(w, 0f, 0.62f, 220);
        }
        panel.animate().alpha(1f).translationY(0f)
                .setDuration(230)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
    }

    private static void dismissSheet(Dialog d, View panel, Window w, OnItem callback, int index) {
        if (panel.getTag() != null) return;
        panel.setTag(Boolean.TRUE);
        panel.setClickable(false);
        if (w != null) animateDim(w, w.getAttributes().dimAmount, 0f, 150);
        panel.animate().alpha(0f).translationY(Ui.dp(panel.getContext(), 22))
                .setDuration(150)
                .withEndAction(() -> {
                    try {
                        d.dismiss();
                    } catch (Throwable ignored) {
                    }
                    callback.onItem(index);
                }).start();
    }

    private static void animateDim(Window w, float from, float to, long duration) {
        if (w == null) return;
        try {
            ValueAnimator va = ValueAnimator.ofFloat(from, to);
            va.setDuration(duration);
            va.addUpdateListener(a -> {
                try {
                    w.setDimAmount((float) a.getAnimatedValue());
                } catch (Throwable ignored) {
                }
            });
            va.start();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Left-anchored drawer for the hamburger menu. The rest of the app uses
     * bottom sheets; this one grows out of the left edge with a staggered
     * list reveal, so the motion matches where the button lives.
     */
    public static void showSideMenu(Activity a, String header, List<Item> items, OnItem callback) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setCanceledOnTouchOutside(false);

        FrameLayout root = new FrameLayout(a);
        View scrim = new View(a);
        scrim.setBackgroundColor(0x77000000);
        scrim.setAlpha(0f);
        root.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF14141A);
        bg.setCornerRadius(Ui.dp(a, 20));
        bg.setStroke(Math.max(1, Ui.dp(a, 1)), 0xFF2C2C36);
        panel.setBackground(bg);
        panel.setPadding(Ui.dp(a, 9), Ui.dp(a, 12), Ui.dp(a, 9), Ui.dp(a, 8));
        panel.setClickable(true);
        panel.setElevation(Ui.dp(a, 10));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                Ui.dp(a, 232), ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.START | Gravity.TOP;
        plp.leftMargin = Ui.dp(a, 8);
        plp.topMargin = Ui.dp(a, 8);
        root.addView(panel, plp);

        TextView head = Ui.mediumText(a, header, 11, Ui.MUTED);
        head.setPadding(Ui.dp(a, 10), 0, 0, Ui.dp(a, 8));
        panel.addView(head);

        List<View> rows = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            Item item = items.get(i);
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(a, 11), 0, Ui.dp(a, 11), 0);
            row.setBackground(Ui.ripple(0x22FFFFFF, 12, a));
            row.setClickable(true);
            IconView icon = new IconView(a, item.icon, item.danger ? Ui.ERROR : Ui.MUTED, 21);
            row.addView(icon);
            TextView label = Ui.text(a, item.label, 15, item.danger ? Ui.ERROR : item.color);
            label.setPadding(Ui.dp(a, 13), 0, 0, 0);
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.setOnClickListener(v -> closeSideMenu(d, root, panel, callback, index));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 45));
            rlp.setMargins(0, Ui.dp(a, 1), 0, Ui.dp(a, 1));
            panel.addView(row, rlp);
            rows.add(row);
        }

        TextView foot = Ui.text(a, "DeepSeek 腕上", 9.5f, Ui.DIM);
        foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = Ui.dp(a, 6);
        panel.addView(foot, flp);

        scrim.setOnClickListener(v -> closeSideMenu(d, root, panel, callback, -1));
        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setGravity(Gravity.START | Gravity.TOP);
        }

        panel.setTranslationX(-Ui.dp(a, 250));
        d.show();
        if (w != null) w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        scrim.animate().alpha(1f).setDuration(220).start();
        panel.animate().translationX(0f).alpha(1f).setDuration(280)
                .setInterpolator(new DecelerateInterpolator(2f)).start();
        for (int i = 0; i < rows.size(); i++) {
            View row = rows.get(i);
            row.setAlpha(0f);
            row.setTranslationX(-Ui.dp(a, 12));
            row.animate().alpha(1f).translationX(0f)
                    .setStartDelay(55 + i * 32L)
                    .setDuration(190)
                    .setInterpolator(new DecelerateInterpolator(1.4f))
                    .start();
        }
    }

    private static void closeSideMenu(Dialog d, View root, View panel, OnItem callback, int index) {
        if (root.getTag() != null) return;
        root.setTag(Boolean.TRUE);
        root.setClickable(false);
        root.animate().alpha(0f).setDuration(170).start();
        panel.animate().translationX(-Ui.dp(panel.getContext(), 250)).alpha(0.9f)
                .setDuration(180)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .withEndAction(() -> {
                    try {
                        d.dismiss();
                    } catch (Throwable ignored) {
                    }
                    if (index >= 0) callback.onItem(index);
                }).start();
    }

    public static void confirm(Activity a, String title, String message,
                               String positive, OnConfirm callback) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = Ui.round(0xFF17171D, 22, a);
        panel.setBackground(bg);
        panel.setPadding(Ui.dp(a, 18), Ui.dp(a, 18), Ui.dp(a, 18), Ui.dp(a, 14));

        TextView t = Ui.mediumText(a, title, 17, Ui.TEXT);
        panel.addView(t);
        if (!TextUtils.isEmpty(message)) {
            TextView m = Ui.text(a, message, 13, Ui.MUTED);
            m.setPadding(0, Ui.dp(a, 9), 0, 0);
            panel.addView(m);
        }

        LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, Ui.dp(a, 18), 0, 0);

        TextView cancel = Ui.mediumText(a, "取消", 14, Ui.MUTED);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(Ui.ripple(0x22FFFFFF, 12, a));
        cancel.setClickable(true);
        cancel.setOnClickListener(v -> d.dismiss());
        buttons.addView(cancel, new LinearLayout.LayoutParams(Ui.dp(a, 68), Ui.dp(a, 38)));

        TextView ok = Ui.mediumText(a, positive, 14, Ui.ERROR);
        ok.setGravity(Gravity.CENTER);
        ok.setBackground(Ui.ripple(0x22FF7373, 12, a));
        ok.setClickable(true);
        ok.setOnClickListener(v -> {
            d.dismiss();
            callback.onConfirm();
        });
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(Ui.dp(a, 84), Ui.dp(a, 38));
        olp.leftMargin = Ui.dp(a, 4);
        buttons.addView(ok, olp);
        panel.addView(buttons);

        d.setContentView(panel);
        showCentered(d, a, 280);
    }

    public static void showInput(Activity a, String title, String hint, String initial,
                                 boolean secret, OnText callback) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.round(0xFF17171D, 22, a));
        panel.setPadding(Ui.dp(a, 16), Ui.dp(a, 16), Ui.dp(a, 16), Ui.dp(a, 12));

        TextView t = Ui.mediumText(a, title, 16, Ui.TEXT);
        panel.addView(t);

        EditText input = new EditText(a);
        input.setText(initial == null ? "" : initial);
        input.setHint(hint);
        input.setHintTextColor(Ui.DIM);
        input.setTextColor(Ui.TEXT);
        input.setTextSize(14);
        input.setSingleLine(false);
        input.setMaxLines(4);
        input.setPadding(Ui.dp(a, 11), Ui.dp(a, 9), Ui.dp(a, 11), Ui.dp(a, 9));
        input.setBackground(Ui.roundStroke(0xFF101014, 12, Ui.BORDER, 1, a));
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | (secret ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_FLAG_MULTI_LINE));
        if (secret) input.setSingleLine(true);
        panel.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, Ui.dp(a, 10), 0, 0);

        TextView cancel = Ui.mediumText(a, "取消", 14, Ui.MUTED);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(Ui.ripple(0x22FFFFFF, 10, a));
        cancel.setClickable(true);
        cancel.setOnClickListener(v -> d.dismiss());
        buttons.addView(cancel, new LinearLayout.LayoutParams(Ui.dp(a, 64), Ui.dp(a, 38)));

        TextView ok = Ui.mediumText(a, "保存", 14, Ui.BLUE_LIGHT);
        ok.setGravity(Gravity.CENTER);
        ok.setBackground(Ui.ripple(0x334D6BFE, 10, a));
        ok.setClickable(true);
        ok.setOnClickListener(v -> {
            String value = input.getText().toString();
            d.dismiss();
            callback.onText(value);
        });
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(Ui.dp(a, 76), Ui.dp(a, 38));
        olp.leftMargin = Ui.dp(a, 4);
        buttons.addView(ok, olp);
        panel.addView(buttons);

        d.setContentView(panel);
        showCentered(d, a, 286);
        input.postDelayed(() -> {
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 180);
    }

    public static void showInfo(Activity a, String title, String message, String positive) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.round(0xFF17171D, 22, a));
        panel.setPadding(Ui.dp(a, 18), Ui.dp(a, 18), Ui.dp(a, 18), Ui.dp(a, 14));
        panel.addView(Ui.mediumText(a, title, 17, Ui.TEXT));
        ScrollView scroll = new ScrollView(a);
        TextView m = Ui.text(a, message, 13, Ui.MUTED);
        m.setPadding(0, Ui.dp(a, 10), 0, 0);
        m.setLineSpacing(Ui.dp(a, 4), 1f);
        scroll.addView(m);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(a, 2);
        panel.addView(scroll, slp);
        TextView ok = Ui.mediumText(a, positive, 14, Ui.BLUE_LIGHT);
        ok.setGravity(Gravity.CENTER);
        ok.setBackground(Ui.ripple(0x334D6BFE, 10, a));
        ok.setClickable(true);
        ok.setOnClickListener(v -> d.dismiss());
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 40));
        olp.topMargin = Ui.dp(a, 14);
        panel.addView(ok, olp);
        d.setContentView(panel);
        showCentered(d, a, 292);
    }

    private static void showCentered(Dialog d, Activity a, int widthDp) {
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.66f);
            w.setGravity(Gravity.CENTER);
            w.setLayout(Ui.dp(a, widthDp), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.show();
        if (w != null) {
            int maxW = a.getResources().getDisplayMetrics().widthPixels - Ui.dp(a, 24);
            w.setLayout(Math.min(Ui.dp(a, widthDp), maxW), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}
