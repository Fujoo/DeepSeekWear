package com.wrist.deepseek.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wrist.deepseek.App;
import com.wrist.deepseek.core.Settings;
import com.wrist.deepseek.util.Ui;

/** First-run density calibration, also reachable from Personalization. */
public class ScaleActivity extends Activity {
    private Settings settings;
    private boolean adjustOnly;
    private TextView percent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = App.get(this).settings;
        adjustOnly = getIntent().getBooleanExtra("adjust_only", false);
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
        bar.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 8), 0);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 45)));
        IconView back = new IconView(this, IconView.BACK, Ui.TEXT, 34);
        back.setBackground(Ui.ripple(0x1FFFFFFF, 17, this));
        back.setOnClickListener(v -> complete());
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
        TextView title = Ui.mediumText(this, adjustOnly ? "界面缩放" : "界面适配", 16, Ui.TEXT);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tlp.leftMargin = Ui.dp(this, 6);
        bar.addView(title, tlp);
        TextView skip = Ui.mediumText(this, adjustOnly ? "关闭" : "跳过", 12, Ui.MUTED);
        skip.setGravity(Gravity.CENTER);
        skip.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 6), 0);
        skip.setClickable(true);
        skip.setOnClickListener(v -> complete());
        bar.addView(skip);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 18), Ui.dp(this, 4), Ui.dp(this, 18), Ui.dp(this, 18));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView heading = Ui.mediumText(this, "调整界面大小", 18, Ui.TEXT);
        content.addView(heading);
        TextView sub = Ui.text(this, "不同手表分辨率不同，调到文字和按钮刚好合适即可。",
                10.5f, Ui.MUTED);
        sub.setLineSpacing(Ui.dp(this, 2), 1);
        sub.setPadding(0, Ui.dp(this, 5), 0, 0);
        content.addView(sub);

        content.addView(buildPreview(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 58));
        clp.topMargin = Ui.dp(this, 12);
        content.addView(controls, clp);

        TextView minus = Ui.mediumText(this, "−", 24, Ui.TEXT);
        minus.setGravity(Gravity.CENTER);
        minus.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x33FFFFFF),
                Ui.roundStroke(0xFF101822, 21, 0xFF2A364A, 1, this),
                Ui.round(Color.WHITE, 21, this)));
        minus.setClickable(true);
        minus.setOnClickListener(v -> adjust(-0.05f));
        controls.addView(minus, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1));

        percent = Ui.mediumText(this, percentText(), 20, Ui.BLUE_LIGHT);
        percent.setGravity(Gravity.CENTER);
        controls.addView(percent, new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 2));

        TextView plus = Ui.mediumText(this, "+", 24, Ui.TEXT);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x33FFFFFF),
                Ui.roundStroke(0xFF101822, 21, 0xFF2A364A, 1, this),
                Ui.round(Color.WHITE, 21, this)));
        plus.setClickable(true);
        plus.setOnClickListener(v -> adjust(0.05f));
        controls.addView(plus, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1));

        TextView done = Ui.mediumText(this, adjustOnly ? "应用缩放" : "完成，开始使用",
                13, Color.WHITE);
        done.setGravity(Gravity.CENTER);
        done.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x33FFFFFF),
                Ui.round(Ui.ACCENT, 20, this), Ui.round(Color.WHITE, 20, this)));
        done.setClickable(true);
        done.setOnClickListener(v -> complete());
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 42));
        dlp.topMargin = Ui.dp(this, 12);
        content.addView(done, dlp);

        TextView reset = Ui.mediumText(this, "恢复默认 100%", 11, Ui.MUTED);
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(0, Ui.dp(this, 12), 0, 0);
        reset.setClickable(true);
        reset.setOnClickListener(v -> {
            settings.setUiScale(1f);
            recreate();
        });
        content.addView(reset);

        TextView note = Ui.text(this, "应用后会重启界面以生效", 9.5f, Ui.DIM);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, Ui.dp(this, 6), 0, 0);
        content.addView(note);

        setContentView(root);
        root.requestApplyInsets();
    }

    private String percentText() {
        return Math.round(settings.getUiScale() * 100) + "%";
    }

    private View buildPreview() {
        FrameLayout stage = new FrameLayout(this);
        stage.setClipChildren(true);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 210));
        slp.topMargin = Ui.dp(this, 10);
        stage.setLayoutParams(slp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.roundStroke(0xFF0A1018, 18, 0xFF2A3A55, 1, this));
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 11));
        card.setScaleX(settings.getUiScale());
        card.setScaleY(settings.getUiScale());
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                Ui.dp(this, 260), ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.CENTER;
        stage.addView(card, clp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView clock = Ui.mediumText(this, "10:08", 12, Ui.TEXT);
        top.addView(clock, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView name = Ui.mediumText(this, "DeepSeek", 13, Ui.TEXT);
        top.addView(name);
        TextView plus = Ui.mediumText(this, "+", 20, Ui.TEXT);
        plus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                Ui.dp(this, 26), Ui.dp(this, 26));
        plp.leftMargin = Ui.dp(this, 10);
        top.addView(plus, plp);
        card.addView(top);

        TextView bubble = Ui.text(this, "你好，我是 DeepSeek", 12, Ui.TEXT);
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackground(Ui.round(0xFF2448C9, 14, this));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.END;
        blp.topMargin = Ui.dp(this, 10);
        card.addView(bubble, blp);
        bubble.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 12), Ui.dp(this, 7));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 7), Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5));
        bar.setBackground(Ui.roundStroke(0xFF0B1424, 20, 0xFF2B3D59, 1, this));
        LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp2.topMargin = Ui.dp(this, 12);
        card.addView(bar, blp2);
        IconView globe = new IconView(this, IconView.GLOBE, 0xFFE6ECF7, 15);
        bar.addView(globe, new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28)));
        TextView hint = Ui.text(this, "给 DeepSeek 发消息", 11, 0xFF7F8BA0);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        hlp.leftMargin = Ui.dp(this, 7);
        bar.addView(hint, hlp);
        IconView mic = new IconView(this, IconView.MIC, 0xFFDDEBFF, 16);
        mic.setBackground(Ui.round(0xFF14243D, 16, this));
        bar.addView(mic, new LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32)));

        return stage;
    }

    private void adjust(float delta) {
        float current = settings.getUiScale();
        float next = Math.round((current + delta) * 100f) / 100f;
        next = Math.max(Settings.MIN_UI_SCALE, Math.min(Settings.MAX_UI_SCALE, next));
        if (Math.abs(next - current) < 0.001f) return;
        settings.setUiScale(next);
        recreate();
    }

    private void complete() {
        settings.setScaleConfigured(true);
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    @Override
    public void onBackPressed() {
        complete();
    }
}
