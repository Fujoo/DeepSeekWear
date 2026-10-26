package com.wrist.deepseek.ui;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Outline;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wrist.deepseek.R;
import com.wrist.deepseek.util.Ui;

/** About: avatar, author, changelog, and the closing illustration. */
public class AboutActivity extends ScaledActivity {
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupWindow();
        buildUi();
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

    private void buildUi() {
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
        TextView title = Ui.mediumText(this, "关于", 16, Ui.TEXT);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tlp.leftMargin = Ui.dp(this, 6);
        bar.addView(title, tlp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 28));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Avatar + name.
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout avatarFrame = new FrameLayout(this);
        avatarFrame.setBackground(Ui.roundStroke(0xFF17171D, 30, 0xFF343440, 1.5f, this));
        avatarFrame.setPadding(Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2));
        ImageView avatar = new ImageView(this);
        avatar.setImageResource(R.drawable.about_avatar);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setClipToOutline(true);
        avatar.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        avatarFrame.addView(avatar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        hero.addView(avatarFrame, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));

        LinearLayout nameBox = new LinearLayout(this);
        nameBox.setOrientation(LinearLayout.VERTICAL);
        nameBox.setPadding(Ui.dp(this, 13), 0, 0, 0);
        TextView name = Ui.mediumText(this, "Fujoo", 21, Ui.TEXT);
        nameBox.addView(name);
        TextView date = Ui.text(this, "2026.10.23", 10.5f, Ui.MUTED);
        date.setPadding(0, Ui.dp(this, 3), 0, 0);
        nameBox.addView(date);
        hero.addView(nameBox);
        content.addView(hero);

        // A deliberate gap between the identity and the changelog.
        View gap = new View(this);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(1, Ui.dp(this, 30));
        content.addView(gap, glp);

        TextView changelog = Ui.mediumText(this, "版本更新", 12, Ui.MUTED);
        content.addView(changelog);

        String version = "1.7.0";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        addVersion("v" + version, true,
                "修复 LaTeX 数学公式显示：分式、组合数、概率符号、方框与上下标",
                "块级公式居中显示，不再出现原始 LaTeX 代码");

        addVersion("v1.6.0", false,
                "麦克风录音上限从 45 秒提升到 5 分钟",
                "语音转写上传/等待时间同步放宽");

        addVersion("v1.5.0", false,
                "输入栏改为深色胶囊：圆形搜索、竖分隔、磨砂暗深蓝麦克风",
                "语音识别默认模型改为 TeleSpeechASR，旧 SenseVoice 自动迁移",
                "首次启动加入界面缩放适配，教程前可调整",
                "设置 → 个性化 可重新调整界面缩放",
                "新增首次使用教程，可从设置再次打开",
                "左上角菜单上方显示当前时间，个性化可关闭",
                "地球图标补上两条纬线",
                "主页移除软件图标，欢迎页更干净");

        addVersion("v1.4.0", false,
                "设置新增“个性化”，可切换侧滑 / 底部菜单",
                "PIN 键盘改为颗粒感表冠式震动",
                "左上角菜单收紧为紧凑侧滑，减少空白",
                "新对话切换加入淡出淡入过渡");

        addVersion("v1.3.0", false,
                "PIN 键盘加入线性马达震动",
                "按住说话支持上滑取消，松手不发送",
                "移除设置中的全局“语音极速”开关");

        addVersion("v1.2.0", false,
                "新增四位密码隐私空间，普通/隐私历史隔离",
                "语音识别模型可选：SenseVoice / TeleSpeech / 自定义",
                "输入框按住说话，松手自动发送",
                "新增“按住说话快速语音”");

        addVersion("v1.1.0", false,
                "接入 DeepSeek 联网搜索",
                "回答带 [1] 引用，来源列表可点开复制",
                "搜索结果本地保存，重启仍在");

        addVersion("v1.0.0", false,
                "DeepSeek V4 流式聊天与深度思考",
                "语音输入、悄悄话增强、上下文纠错",
                "历史记录、TTS 朗读、手机网页配置",
                "专为 32 位安卓手表设计的极简 UI");

        setContentView(root);
        root.requestApplyInsets();
    }

    private void addVersion(String version, boolean current, String... lines) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.roundStroke(0xFF141419, 15,
                current ? 0xFF4058D8 : Ui.BORDER, current ? 1.4f : 1, this));
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 11));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView v = Ui.mediumText(this, version, current ? 14 : 13,
                current ? Ui.BLUE_LIGHT : Ui.MUTED);
        header.addView(v);
        if (current) {
            TextView pill = Ui.mediumText(this, "当前", 9, Color.WHITE);
            pill.setGravity(Gravity.CENTER);
            pill.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
            pill.setBackground(Ui.round(Ui.ACCENT, 7, this));
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 17));
            plp.leftMargin = Ui.dp(this, 7);
            header.addView(pill, plp);
        }
        card.addView(header);

        StringBuilder b = new StringBuilder();
        for (String line : lines) {
            if (b.length() > 0) b.append('\n');
            b.append("· ").append(line);
        }
        TextView body = Ui.text(this, b.toString(), 11.5f, 0xFFB9BBC8);
        body.setLineSpacing(Ui.dp(this, 3), 1);
        body.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(body);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 9);
        content.addView(card, lp);
    }
}
