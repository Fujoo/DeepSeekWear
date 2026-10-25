package com.wrist.deepseek.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.wrist.deepseek.util.Ui;

/** First-run, four-step guide. Also reachable from Settings. */
public class TutorialActivity extends ScaledActivity {
    private int page;
    private boolean fromSettings;
    private TextView stepLabel;
    private TextView heading;
    private TextView body;
    private TextView skip;
    private TextView prev;
    private TextView next;
    private IconView icon;
    private LinearLayout dots;
    private LinearLayout slide;

    private static final int[] ICONS = {
            IconView.SPARK, IconView.GLOBE, IconView.MIC, IconView.LOCK
    };
    private static final String[] HEADINGS = {
            "聊天与深度思考", "联网搜索", "按住说话", "隐私与个性化"
    };
    private static final String[] BODIES = {
            "点右上角 + 开始新对话\n点左上角菜单查看历史、隐私空间和设置\n点顶部标题切换快速对话 / 深度思考",
            "输入栏左侧地球开启联网搜索\n回答会带 [1] 引用，下方可查看来源\n点来源列表可复制链接",
            "轻点输入框打字\n按住输入框说话，松手自动发送\n上滑取消\n麦克风图标点击后可录音，手动点击确认后才识别你说的话",
            "菜单 → 隐私空间：四位密码隔离对话\n设置 → 个性化：切换菜单样式、关闭时间\n设置 → 语音输入：切换识别模型"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fromSettings = getIntent().getBooleanExtra("from_settings", false);
        setupWindow();
        build();
        render(false);
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
        bar.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 8), 0);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 45)));
        IconView back = new IconView(this, IconView.BACK, Ui.TEXT, 34);
        back.setBackground(Ui.ripple(0x1FFFFFFF, 17, this));
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
        TextView title = Ui.mediumText(this, "使用教程", 16, Ui.TEXT);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tlp.leftMargin = Ui.dp(this, 6);
        bar.addView(title, tlp);
        skip = Ui.mediumText(this, fromSettings ? "关闭" : "跳过", 12, Ui.MUTED);
        skip.setGravity(Gravity.CENTER);
        skip.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 6), 0);
        skip.setClickable(true);
        skip.setOnClickListener(v -> finish());
        bar.addView(skip);

        LinearLayout bodyBox = new LinearLayout(this);
        bodyBox.setOrientation(LinearLayout.VERTICAL);
        bodyBox.setGravity(Gravity.CENTER);
        bodyBox.setPadding(Ui.dp(this, 22), 0, Ui.dp(this, 22), 0);
        root.addView(bodyBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        slide = new LinearLayout(this);
        slide.setOrientation(LinearLayout.VERTICAL);
        slide.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout iconBox = new LinearLayout(this);
        iconBox.setGravity(Gravity.CENTER);
        iconBox.setBackground(Ui.roundStroke(0xFF17243A, 38, 0xFF2D4568, 1, this));
        icon = new IconView(this, ICONS[0], 0xFF9FC0FF, 40);
        iconBox.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        slide.addView(iconBox, new LinearLayout.LayoutParams(Ui.dp(this, 78), Ui.dp(this, 78)));

        stepLabel = Ui.mediumText(this, "01 / 04", 10, Ui.BLUE_LIGHT);
        stepLabel.setGravity(Gravity.CENTER);
        stepLabel.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(this, 12);
        slide.addView(stepLabel, slp);

        heading = Ui.mediumText(this, HEADINGS[0], 18, Ui.TEXT);
        heading.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = Ui.dp(this, 7);
        slide.addView(heading, hlp);

        body = Ui.text(this, BODIES[0], 11.5f, 0xFFB9BECA);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(Ui.dp(this, 4), 1);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(this, 9);
        slide.addView(body, blp);

        bodyBox.addView(slide);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(Ui.dp(this, 22), 0, Ui.dp(this, 22), Ui.dp(this, 14));
        root.addView(bottom, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        bottom.addView(dots, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 14)));
        rebuildDots();

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 42));
        blp2.topMargin = Ui.dp(this, 6);
        bottom.addView(buttons, blp2);

        prev = Ui.mediumText(this, "上一步", 12, Ui.MUTED);
        prev.setGravity(Gravity.CENTER);
        prev.setClickable(true);
        prev.setOnClickListener(v -> {
            if (page > 0) {
                page--;
                render(true);
            }
        });
        buttons.addView(prev, new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1));

        next = Ui.mediumText(this, "下一步", 13, Color.WHITE);
        next.setGravity(Gravity.CENTER);
        next.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                Ui.round(Ui.ACCENT, 20, this), Ui.round(Color.WHITE, 20, this)));
        next.setClickable(true);
        next.setOnClickListener(v -> {
            if (page < HEADINGS.length - 1) {
                page++;
                render(true);
            } else {
                finish();
            }
        });
        buttons.addView(next, new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 2.2f));

        setContentView(root);
        root.requestApplyInsets();
    }

    private void rebuildDots() {
        dots.removeAllViews();
        for (int i = 0; i < HEADINGS.length; i++) {
            View d = new View(this);
            d.setBackground(Ui.round(i == page ? Ui.ACCENT : 0xFF3A3F4C,
                    i == page ? 3.5f : 2.5f, this));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    Ui.dp(this, i == page ? 16 : 5), Ui.dp(this, 5));
            lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
            dots.addView(d, lp);
        }
    }

    private void render(boolean animate) {
        icon.setIcon(ICONS[page]);
        stepLabel.setText(String.format(java.util.Locale.US, "%02d / %02d",
                page + 1, HEADINGS.length));
        heading.setText(HEADINGS[page]);
        body.setText(BODIES[page]);
        next.setText(page == HEADINGS.length - 1 ? "开始使用" : "下一步");
        prev.setVisibility(page > 0 ? View.VISIBLE : View.INVISIBLE);
        rebuildDots();

        if (animate) {
            slide.animate().cancel();
            slide.setAlpha(0f);
            slide.setTranslationX(Ui.dp(this, 12));
            slide.animate().alpha(1f).translationX(0f)
                    .setDuration(210)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                    .start();
        }
    }
}
