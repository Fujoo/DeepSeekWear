package com.wrist.deepseek.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.wrist.deepseek.App;
import com.wrist.deepseek.core.ChatStore;
import com.wrist.deepseek.core.DeepSeekClient;
import com.wrist.deepseek.core.Settings;
import com.wrist.deepseek.core.SetupServer;
import com.wrist.deepseek.util.Ui;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends ScaledActivity {
    private Settings settings;
    private ChatStore store;
    private ScrollView scroll;
    private TextView dsKeyValue;
    private TextView dsBaseValue;
    private TextView modelValue;
    private TextView asrKeyValue;
    private TextView asrServiceValue;
    private TextView asrModelValue;
    private TextView privacyValue;
    private TextView personalizationValue;
    private SetupServer server;
    private Dialog phoneDialog;
    private boolean phoneOpened;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App app = App.get(this);
        settings = app.settings;
        store = app.store;
        setupWindow();
        buildUi();
        refreshValues();
        String focus = getIntent().getStringExtra("focus");
        if ("deepseek".equals(focus)) {
            scroll.postDelayed(() -> editKey(true), 260);
        } else if ("asr".equals(focus)) {
            scroll.postDelayed(() -> editKey(false), 260);
        } else if ("phone".equals(focus)) {
            scroll.postDelayed(this::openPhoneSetup, 260);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (personalizationValue != null) {
            personalizationValue.setText(settings.isSideMenu() ? "侧滑菜单" : "底部菜单");
        }
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
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
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
        TextView title = Ui.mediumText(this, "设置", 16, Ui.TEXT);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tlp.leftMargin = Ui.dp(this, 6);
        bar.addView(title, tlp);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 10), Ui.dp(this, 2), Ui.dp(this, 10), Ui.dp(this, 24));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // DeepSeek
        content.addView(sectionTitle("DeepSeek"));
        LinearLayout dsCard = sectionCard();
        content.addView(dsCard);
        dsKeyValue = addRow(dsCard, IconView.EDIT, "API Key", "未设置", v -> editKey(true), false);
        dsBaseValue = addRow(dsCard, IconView.GLOBE, "接口地址", "", v -> editBase(), false);
        modelValue = addRow(dsCard, IconView.SPARK, "对话模型", "", v -> editModel(), false);
        addSwitchRow(dsCard, IconView.GLOBE, "联网搜索", settings.isSearchEnabled(), value -> {
            settings.setSearchEnabled(value);
        }, false);
        addRow(dsCard, IconView.CHECK, "测试连接", "确认 Key 是否可用", v -> testConnection(), true);

        // Voice
        content.addView(sectionTitle("语音输入"));
        LinearLayout voiceCard = sectionCard();
        content.addView(voiceCard);
        asrKeyValue = addRow(voiceCard, IconView.EDIT, "语音识别 Key", "未设置", v -> editKey(false), false);
        asrServiceValue = addRow(voiceCard, IconView.SPEAKER, "识别服务", "", v -> chooseAsrPreset(), false);
        asrModelValue = addRow(voiceCard, IconView.SPEAKER, "识别模型", "", v -> chooseAsrModel(), false);
        addSwitchRow(voiceCard, IconView.MIC, "悄悄话增强", settings.isWhisperMode(), value -> {
            settings.setWhisperMode(value);
        }, false);
        addSwitchRow(voiceCard, IconView.MIC, "按住说话快速语音", settings.isHoldFastVoice(), value -> {
            settings.setHoldFastVoice(value);
        }, false);
        addSwitchRow(voiceCard, IconView.SPARK, "结合上下文纠错", settings.isAutoCorrect(), value -> {
            settings.setAutoCorrect(value);
        }, false);
        addSwitchRow(voiceCard, IconView.SPEAKER, "自动朗读回复", settings.isTtsEnabled(), value -> {
            settings.setTtsEnabled(value);
        }, true);

        // General
        content.addView(sectionTitle("通用"));
        LinearLayout general = sectionCard();
        content.addView(general);
        addRow(general, IconView.PHONE, "用手机配置", "推荐 · 不用在手表上打字", v -> openPhoneSetup(), false);
        addSwitchRow(general, IconView.SPARK, "回答更简洁", settings.isCompactPrompt(), value -> {
            settings.setCompactPrompt(value);
        }, false);
        personalizationValue = addRow(general, IconView.MENU, "个性化",
                settings.isSideMenu() ? "侧滑菜单" : "底部菜单",
                v -> openPersonalization(), false);
        privacyValue = addRow(general, IconView.LOCK, "隐私空间", "未设置", v -> openPrivacySettings(), false);
        addRow(general, IconView.TRASH, "清空普通对话", "隐私空间会保留", v -> clearAll(), false);
        addRow(general, IconView.SPARK, "使用教程", "4 步上手", v -> openTutorial(), false);
        addRow(general, IconView.MORE, "关于", "版本与隐私", v -> about(), true);

        setContentView(root);
        root.requestApplyInsets();
    }

    private TextView sectionTitle(String title) {
        TextView t = Ui.mediumText(this, title, 11, Ui.DIM);
        t.setPadding(Ui.dp(this, 5), Ui.dp(this, 15), 0, Ui.dp(this, 6));
        return t;
    }

    private LinearLayout sectionCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.roundStroke(0xFF141419, 15, Ui.BORDER, 1, this));
        card.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(lp);
        return card;
    }

    private TextView addRow(LinearLayout card, int icon, String title, String value,
                            View.OnClickListener click, boolean last) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 11), 0, Ui.dp(this, 9), 0);
        row.setBackground(Ui.ripple(0x1FFFFFFF, 13, this));
        row.setClickable(true);
        row.setOnClickListener(click);

        IconView ic = new IconView(this, icon, Ui.MUTED, 19);
        ic.setClickable(false);
        row.addView(ic);
        TextView t = Ui.text(this, title, 13.5f, Ui.TEXT);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tlp.leftMargin = Ui.dp(this, 10);
        row.addView(t, tlp);
        TextView v = Ui.text(this, value, 10.5f, Ui.DIM);
        v.setMaxLines(1);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setGravity(Gravity.END);
        v.setMaxWidth(Ui.dp(this, 128));
        row.addView(v);
        if (!last) {
            IconView chevron = new IconView(this, IconView.CHEVRON_RIGHT, 0xFF50505C, 14);
            chevron.setClickable(false);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Ui.dp(this, 14), Ui.dp(this, 14));
            clp.leftMargin = Ui.dp(this, 2);
            row.addView(chevron, clp);
        }
        card.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 49)));
        if (!last) addDivider(card);
        return v;
    }

    private void addSwitchRow(LinearLayout card, int icon, String title, boolean checked,
                              SwitchListener listener, boolean last) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 11), 0, Ui.dp(this, 10), 0);
        row.setClickable(true);
        row.setBackground(Ui.ripple(0x1FFFFFFF, 13, this));
        IconView ic = new IconView(this, icon, Ui.MUTED, 19);
        ic.setClickable(false);
        row.addView(ic);
        TextView t = Ui.text(this, title, 13.5f, Ui.TEXT);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tlp.leftMargin = Ui.dp(this, 10);
        row.addView(t, tlp);

        Switch sw = new Switch(this);
        sw.setChecked(checked);
        sw.setClickable(false);
        sw.setThumbTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Color.WHITE, 0xFFD8D8DE}));
        sw.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Ui.ACCENT, 0xFF3A3A46}));
        row.addView(sw, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 32)));
        row.setOnClickListener(v -> {
            boolean next = !sw.isChecked();
            sw.setChecked(next);
            listener.onChanged(next);
        });
        card.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 49)));
        if (!last) addDivider(card);
    }

    private interface SwitchListener {
        void onChanged(boolean value);
    }

    private void addDivider(LinearLayout card) {
        View d = new View(this);
        d.setBackgroundColor(Ui.BORDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = Ui.dp(this, 40);
        card.addView(d, lp);
    }

    private void refreshValues() {
        if (dsKeyValue == null) return;
        dsKeyValue.setText(settings.hasDeepSeekKey() ? settings.keyHint(settings.getDeepSeekKey()) : "未设置");
        dsKeyValue.setTextColor(settings.hasDeepSeekKey() ? Ui.MUTED : Ui.ERROR);
        dsBaseValue.setText(hostOf(settings.getDeepSeekBase()));
        modelValue.setText(settings.getModel());
        asrKeyValue.setText(settings.hasAsrKey() ? settings.keyHint(settings.getAsrKey()) : "未设置");
        asrKeyValue.setTextColor(settings.hasAsrKey() ? Ui.MUTED : Ui.ERROR);
        asrServiceValue.setText(asrLabel());
        asrModelValue.setText(modelLabel());
        if (privacyValue != null) {
            privacyValue.setText(settings.hasPrivacyPin() ? "已设置 · 可修改" : "未设置");
            privacyValue.setTextColor(settings.hasPrivacyPin() ? Ui.MUTED : Ui.DIM);
        }
    }

    private static String hostOf(String url) {
        if (url == null) return "";
        try {
            java.net.URL u = new java.net.URL(url);
            return u.getHost();
        } catch (Throwable t) {
            return url;
        }
    }

    private String modelLabel() {
        String m = settings.getAsrModel();
        if (m.contains("SenseVoice")) return "SenseVoiceSmall";
        if (m.contains("TeleSpeech")) return "TeleSpeechASR";
        return m;
    }

    private void chooseAsrModel() {
        List<Widgets.Item> items = new ArrayList<>();
        items.add(new Widgets.Item("SenseVoiceSmall · 高精度", IconView.CHECK));
        items.add(new Widgets.Item("TeleSpeechASR · 推荐 · 更快", IconView.SPARK));
        items.add(new Widgets.Item("自定义模型名", IconView.EDIT));
        Widgets.showSheet(this, "语音识别模型", items, index -> {
            if (index == 0) {
                settings.setAsrModel("FunAudioLLM/SenseVoiceSmall");
                refreshValues();
                Ui.toast(this, "已切换到 SenseVoiceSmall");
            } else if (index == 1) {
                settings.setAsrModel(Settings.DEFAULT_ASR_MODEL);
                refreshValues();
                Ui.toast(this, "已切换到 TeleSpeechASR");
            } else {
                Widgets.showInput(this, "自定义识别模型", "例如 FunAudioLLM/SenseVoiceSmall",
                        settings.getAsrModel(), false, value -> {
                            if (!TextUtils.isEmpty(value)) settings.setAsrModel(value.trim());
                            refreshValues();
                            Ui.toast(this, "已保存模型名");
                        });
            }
        });
    }

    private String asrLabel() {
        String e = settings.getAsrEndpoint();
        if (e.contains("siliconflow")) return "硅基流动 SenseVoice";
        if (e.contains("groq")) return "Groq Whisper";
        if (e.contains("openai")) return "OpenAI Whisper";
        return "自定义接口";
    }

    private void editKey(boolean deepseek) {
        String current = deepseek ? settings.getDeepSeekKey() : settings.getAsrKey();
        String title = deepseek ? "DeepSeek API Key" : "语音识别 API Key";
        String hint = deepseek ? "sk-..." : "硅基流动 / OpenAI Key";
        Widgets.showInput(this, title, hint, "", true, value -> {
            if (deepseek) settings.setDeepSeekKey(value);
            else settings.setAsrKey(value);
            refreshValues();
            Ui.toast(this, TextUtils.isEmpty(value) ? "已清除" : "已保存");
            if (!deepseek && !TextUtils.isEmpty(value) && "siliconflow".equals(settings.getAsrPreset())) {
                // Default endpoint/model are already set.
            }
        });
    }

    private void editBase() {
        Widgets.showInput(this, "DeepSeek 接口地址", "https://api.deepseek.com",
                settings.getDeepSeekBase(), false, value -> {
                    settings.setDeepSeekBase(value);
                    refreshValues();
                    Ui.toast(this, "已保存");
                });
    }

    private void editModel() {
        Widgets.showInput(this, "对话模型", "deepseek-flash 或 deepseek-v4-pro",
                settings.getModel(), false, value -> {
            if (!TextUtils.isEmpty(value)) settings.setModel(value.trim());
            refreshValues();
            Ui.toast(this, "已保存");
        });
    }

    private void chooseAsrPreset() {
        List<Widgets.Item> items = new ArrayList<>();
        items.add(new Widgets.Item("硅基流动 · SenseVoice", IconView.CHECK));
        items.add(new Widgets.Item("OpenAI Whisper", IconView.SPEAKER));
        items.add(new Widgets.Item("Groq Whisper", IconView.SPEAKER));
        Widgets.showSheet(this, "语音识别服务", items, index -> {
            if (index == 0) settings.applyAsrPreset("siliconflow");
            else if (index == 1) settings.applyAsrPreset("openai");
            else settings.applyAsrPreset("groq");
            refreshValues();
            Ui.toast(this, "已切换，请确认 Key 对应");
        });
    }

    private void testConnection() {
        if (!settings.hasDeepSeekKey()) {
            Ui.toast(this, "请先填写 DeepSeek API Key");
            return;
        }
        Ui.toast(this, "正在测试连接…");
        new DeepSeekClient().test(settings, (ok, message) -> runOnUiThread(() -> {
            if (ok) {
                Ui.toast(this, "连接正常");
            } else {
                Widgets.showInfo(this, "连接失败", message, "知道了");
            }
        }));
    }

    private void clearAll() {
        Widgets.confirm(this, "清空普通对话？", "普通聊天记录会被删除；隐私空间内的对话会保留。", "清空", () -> {
            store.deleteAllConversations(false);
            settings.setLastConversation(0);
            Ui.toast(this, "普通对话已清空，隐私空间保留");
        });
    }

    private void openTutorial() {
        android.content.Intent i = new android.content.Intent(this, TutorialActivity.class);
        i.putExtra("from_settings", true);
        startActivity(i);
    }

    private void openPersonalization() {
        startActivity(new android.content.Intent(this, PersonalizationActivity.class));
    }

    private void openPrivacySettings() {
        if (!settings.hasPrivacyPin()) {
            PinDialog.show(this, settings, true, this::refreshValues);
            return;
        }
        PinDialog.show(this, settings, false, this::showPrivacyManage);
    }

    private void showPrivacyManage() {
        List<Widgets.Item> items = new ArrayList<>();
        items.add(new Widgets.Item("修改四位密码", IconView.EDIT));
        items.add(new Widgets.Item("清空隐私空间", IconView.TRASH, Ui.ERROR, true));
        Widgets.showSheet(this, "隐私空间", items, index -> {
            if (index == 0) {
                PinDialog.show(this, settings, true, () -> {
                    refreshValues();
                    Ui.toast(this, "隐私密码已更新");
                });
            } else {
                Widgets.confirm(this, "清空隐私空间？", "所有隐私对话和隐私密码都会被删除，普通对话保留。", "清空", () -> {
                    store.deleteAllConversations(true);
                    settings.clearPrivacyPin();
                    settings.setPrivacyLastConversation(0);
                    refreshValues();
                    Ui.toast(this, "隐私空间已清空");
                });
            }
        });
    }

    private void about() {
        startActivity(new android.content.Intent(this, AboutActivity.class));
    }

    private void openPhoneSetup() {
        if (phoneDialog != null && phoneDialog.isShowing()) return;
        if (phoneOpened) return;
        phoneOpened = true;

        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.round(0xFF17171D, 22, this));
        panel.setPadding(Ui.dp(this, 17), Ui.dp(this, 16), Ui.dp(this, 17), Ui.dp(this, 14));

        TextView title = Ui.mediumText(this, "用手机配置", 18, Ui.TEXT);
        panel.addView(title);
        TextView desc = Ui.text(this, "让手机和手表连接同一个 WiFi，然后在手机浏览器打开下面的地址。", 12, Ui.MUTED);
        desc.setPadding(0, Ui.dp(this, 7), 0, 0);
        panel.addView(desc);

        String url = null;
        String error = null;
        try {
            server = new SetupServer(this, settings, () -> runOnUiThread(() -> {
                refreshValues();
                Ui.toast(this, "手机配置已保存");
            }));
            server.start();
            url = server.url();
            if (url == null) error = "手表还没有连接 WiFi";
        } catch (Throwable t) {
            error = "启动配置服务失败：" + t.getMessage();
        }

        TextView urlBox = Ui.text(this, error != null ? error : url, 11, error != null ? Ui.ERROR : Ui.BLUE_LIGHT);
        urlBox.setTextIsSelectable(true);
        urlBox.setPadding(Ui.dp(this, 11), Ui.dp(this, 11), Ui.dp(this, 11), Ui.dp(this, 11));
        urlBox.setBackground(Ui.roundStroke(0xFF0E0E12, 12, Ui.BORDER, 1, this));
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ulp.topMargin = Ui.dp(this, 12);
        panel.addView(urlBox, ulp);

        TextView note = Ui.text(this, "地址只在本次配置期间有效。若打不开，请检查手表 WiFi 和防火墙。", 10.5f, Ui.DIM);
        note.setPadding(0, Ui.dp(this, 8), 0, 0);
        panel.addView(note);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, Ui.dp(this, 14), 0, 0);
        TextView copy = Ui.mediumText(this, "复制链接", 13, Ui.BLUE_LIGHT);
        copy.setGravity(Gravity.CENTER);
        copy.setBackground(Ui.ripple(0x334D6BFE, 11, this));
        copy.setClickable(true);
        final String copyUrl = url;
        copy.setOnClickListener(v -> {
            if (copyUrl != null) Ui.copy(this, copyUrl);
        });
        buttons.addView(copy, new LinearLayout.LayoutParams(Ui.dp(this, 92), Ui.dp(this, 38)));
        TextView close = Ui.mediumText(this, "关闭", 13, Ui.MUTED);
        close.setGravity(Gravity.CENTER);
        close.setBackground(Ui.ripple(0x22FFFFFF, 11, this));
        close.setClickable(true);
        close.setOnClickListener(v -> d.dismiss());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Ui.dp(this, 76), Ui.dp(this, 38));
        clp.leftMargin = Ui.dp(this, 5);
        buttons.addView(close, clp);
        panel.addView(buttons);

        d.setContentView(panel);
        d.setOnDismissListener(x -> {
            if (server != null) {
                server.stop();
                server = null;
            }
            phoneDialog = null;
            phoneOpened = false;
        });
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.64f);
            w.setGravity(Gravity.CENTER);
            int maxW = getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 22);
            w.setLayout(Math.min(Ui.dp(this, 292), maxW), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        phoneDialog = d;
        d.show();
        if (w != null) {
            int maxW = getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 22);
            w.setLayout(Math.min(Ui.dp(this, 292), maxW), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @Override
    protected void onPause() {
        if (phoneDialog != null && phoneDialog.isShowing()) phoneDialog.dismiss();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (server != null) {
            server.stop();
            server = null;
        }
        super.onDestroy();
    }
}
