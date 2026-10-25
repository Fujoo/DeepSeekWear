package com.wrist.deepseek.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wrist.deepseek.App;
import com.wrist.deepseek.R;
import com.wrist.deepseek.core.ChatStore;
import com.wrist.deepseek.core.Conversation;
import com.wrist.deepseek.core.Settings;
import com.wrist.deepseek.util.Ui;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends ScaledActivity {
    private ChatStore store;
    private Settings settings;
    private LinearLayout list;
    private LinearLayout emptyView;
    private View clearAll;
    private long currentId;
    private boolean privateMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App app = App.get(this);
        store = app.store;
        settings = app.settings;
        currentId = getIntent().getLongExtra("current_id", 0);
        privateMode = getIntent().getBooleanExtra("privacy", false);
        setupWindow();
        buildUi();
        rebuild();
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
        TextView title = Ui.mediumText(this, privateMode ? "隐私对话" : "对话", 16, Ui.TEXT);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tlp.leftMargin = Ui.dp(this, 6);
        bar.addView(title, tlp);
        IconView plus = new IconView(this, IconView.PLUS, Ui.TEXT, 34);
        plus.setBackground(Ui.ripple(0x1FFFFFFF, 17, this));
        plus.setOnClickListener(v -> {
            Intent data = new Intent();
            data.putExtra("conversation_id", 0L);
            setResult(RESULT_OK, data);
            finish();
        });
        bar.addView(plus, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 14));
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
        root.requestApplyInsets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuild();
    }

    private void rebuild() {
        if (list == null) return;
        list.removeAllViews();
        List<Conversation> conversations = store.listConversations(privateMode);
        if (conversations.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Ui.dp(this, 70), 0, 0);
            IconView icon = new IconView(this, IconView.HISTORY, Ui.DIM, 40);
            empty.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
            TextView t = Ui.mediumText(this, privateMode ? "还没有隐私对话" : "还没有对话", 15, Ui.MUTED);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = Ui.dp(this, 10);
            empty.addView(t, tlp);
            TextView s = Ui.text(this, privateMode ? "这里的记录只在隐私空间显示" : "回到聊天，说点什么吧", 11, Ui.DIM);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = Ui.dp(this, 5);
            empty.addView(s, slp);
            list.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 220)));
            return;
        }
        for (Conversation c : conversations) {
            list.addView(buildRow(c));
        }
        clearAll = new TextView(this);
        ((TextView) clearAll).setText(privateMode ? "清空隐私对话" : "清空全部对话");
        ((TextView) clearAll).setTextSize(12);
        ((TextView) clearAll).setTextColor(Ui.ERROR);
        ((TextView) clearAll).setGravity(Gravity.CENTER);
        ((TextView) clearAll).setBackground(Ui.ripple(0x22FF7373, 14, this));
        ((TextView) clearAll).setClickable(true);
        ((TextView) clearAll).setOnClickListener(v -> clearAll());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 42));
        clp.topMargin = Ui.dp(this, 14);
        list.addView(clearAll, clp);
    }

    private View buildRow(Conversation c) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(Ui.dp(this, 12), Ui.dp(this, 9), Ui.dp(this, 10), Ui.dp(this, 9));
        GradientDrawable bg = Ui.roundStroke(0xFF15151B, 15,
                c.id == currentId ? 0xFF3D56D8 : Ui.BORDER, c.id == currentId ? 1.2f : 1, this);
        row.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x20FFFFFF), bg,
                Ui.round(Color.WHITE, 15, this)));
        row.setClickable(true);
        row.setOnClickListener(v -> {
            Intent data = new Intent();
            data.putExtra("conversation_id", c.id);
            setResult(RESULT_OK, data);
            finish();
        });
        row.setOnLongClickListener(v -> {
            showActions(c);
            return true;
        });

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.mediumText(this, c.title, 14, Ui.TEXT);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView time = Ui.text(this, Ui.relative(c.updatedAt), 10, Ui.DIM);
        top.addView(time);
        row.addView(top);

        String preview = Ui.oneLine(c.preview, 40);
        TextView sub = Ui.text(this, preview.isEmpty()
                ? (c.messageCount + " 条消息") : preview, 10.5f, Ui.MUTED);
        sub.setMaxLines(1);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(this, 4);
        row.addView(sub, slp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 6);
        row.setLayoutParams(lp);
        return row;
    }

    private void showActions(Conversation c) {
        List<Widgets.Item> items = new ArrayList<>();
        items.add(new Widgets.Item("重命名", IconView.EDIT));
        items.add(new Widgets.Item("删除", IconView.TRASH, Ui.ERROR, true));
        Widgets.showSheet(this, c.title, items, index -> {
            if (index == 0) {
                Widgets.showInput(this, "重命名对话", "输入标题", c.title, false, value -> {
                    store.renameConversation(c.id, Ui.titleFrom(value));
                    rebuild();
                });
            } else {
                Widgets.confirm(this, "删除这个对话？", "删除后无法恢复。", "删除", () -> {
                    store.deleteConversation(c.id);
                    if (c.id == currentId) {
                        settings.setLastConversation(0);
                        Intent data = new Intent();
                        data.putExtra("conversation_id", 0L);
                        setResult(RESULT_OK, data);
                        finish();
                    } else {
                        rebuild();
                    }
                });
            }
        });
    }

    private void clearAll() {
        Widgets.confirm(this, privateMode ? "清空隐私对话？" : "清空全部对话？",
                privateMode ? "隐私空间内的对话会被删除，且无法恢复。" : "所有历史记录都会被删除，且无法恢复。", "清空", () -> {
            store.deleteAllConversations(privateMode);
            settings.setLastConversation(0);
            Intent data = new Intent();
            data.putExtra("conversation_id", 0L);
            setResult(RESULT_OK, data);
            finish();
        });
    }
}
