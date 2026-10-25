package com.wrist.deepseek.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.wrist.deepseek.App;
import com.wrist.deepseek.core.Settings;
import com.wrist.deepseek.util.Ui;

/** Personalization: menu presentation and the clock above the menu. */
public class PersonalizationActivity extends ScaledActivity {
    private Settings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = App.get(this).settings;
        setupWindow();
        build();
    }

    private void setupWindow() {
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 45)));
        IconView back = new IconView(this, IconView.BACK, Ui.TEXT, 34);
        back.setBackground(Ui.ripple(0x1FFFFFFF, 17, this));
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
        TextView title = Ui.mediumText(this, "个性化", 16, Ui.TEXT);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tlp.leftMargin = Ui.dp(this, 6);
        bar.addView(title, tlp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 24));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(section("菜单展开方式"));
        LinearLayout menuCard = card();
        content.addView(menuCard);
        addChoice(menuCard, "侧滑菜单", "从左侧滑出，面板更紧凑", settings.isSideMenu(), () -> {
            settings.setSideMenu(true);
            build();
        });
        addChoice(menuCard, "底部菜单", "从屏幕底部展开", !settings.isSideMenu(), () -> {
            settings.setSideMenu(false);
            build();
        });

        content.addView(section("显示"));
        LinearLayout showCard = card();
        content.addView(showCard);
        addClockRow(showCard);
        addScaleRow(showCard);

        TextView foot = Ui.text(this, "修改会立即生效", 10, Ui.DIM);
        foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = Ui.dp(this, 18);
        content.addView(foot, flp);

        setContentView(root);
        root.requestApplyInsets();
    }

    private TextView section(String text) {
        TextView t = Ui.mediumText(this, text, 11, Ui.DIM);
        t.setPadding(Ui.dp(this, 5), Ui.dp(this, 15), 0, Ui.dp(this, 6));
        return t;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.roundStroke(0xFF141419, 15, Ui.BORDER, 1, this));
        card.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 2));
        card.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private void addChoice(LinearLayout card, String title, String sub,
                           boolean selected, Runnable onPick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 11), 0, Ui.dp(this, 12), 0);
        row.setBackground(Ui.ripple(0x1FFFFFFF, 13, this));
        row.setClickable(true);
        row.setOnClickListener(v -> onPick.run());

        IconView check = new IconView(this, IconView.CHECK,
                selected ? Ui.ACCENT : 0xFF3A3F4C, 20);
        row.addView(check, new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(this, 11), 0, 0, 0);
        TextView t = Ui.mediumText(this, title, 14, Ui.TEXT);
        texts.addView(t);
        TextView s = Ui.text(this, sub, 10, Ui.MUTED);
        s.setPadding(0, Ui.dp(this, 2), 0, 0);
        texts.addView(s);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        card.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 58)));
    }

    private void addScaleRow(LinearLayout card) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        row.setBackground(Ui.ripple(0x1FFFFFFF, 13, this));
        row.setClickable(true);
        row.setOnClickListener(v -> {
            android.content.Intent i = new android.content.Intent(this, ScaleActivity.class);
            i.putExtra("adjust_only", true);
            startActivity(i);
        });

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.mediumText(this, "界面缩放", 14, Ui.TEXT);
        texts.addView(t);
        TextView s = Ui.text(this, "调整整体文字和按钮大小", 10, Ui.MUTED);
        s.setPadding(0, Ui.dp(this, 2), 0, 0);
        texts.addView(s);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView value = Ui.mediumText(this,
                Math.round(settings.getUiScale() * 100) + "%", 12, Ui.BLUE_LIGHT);
        row.addView(value);

        card.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 58)));
    }

    private void addClockRow(LinearLayout card) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.mediumText(this, "显示左上角时间", 14, Ui.TEXT);
        texts.addView(t);
        TextView s = Ui.text(this, "在菜单上方显示当前时间", 10, Ui.MUTED);
        s.setPadding(0, Ui.dp(this, 2), 0, 0);
        texts.addView(s);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Switch sw = new Switch(this);
        sw.setChecked(settings.isShowClock());
        sw.setShowText(false);
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{}
        };
        sw.setTrackTintList(new ColorStateList(states,
                new int[]{0xFF2F5BFF, 0xFF343A48}));
        sw.setThumbTintList(new ColorStateList(states,
                new int[]{Color.WHITE, 0xFFD7DBE5}));
        sw.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            settings.setShowClock(isChecked);
            Ui.toast(this, isChecked ? "已显示时间" : "已关闭时间显示");
        });
        row.addView(sw, new LinearLayout.LayoutParams(
                Ui.dp(this, 48), Ui.dp(this, 40)));

        card.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 64)));
    }
}
