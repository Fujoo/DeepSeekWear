package com.wrist.deepseek.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wrist.deepseek.App;
import com.wrist.deepseek.R;
import com.wrist.deepseek.core.AsrClient;
import com.wrist.deepseek.core.AudioRecorder;
import com.wrist.deepseek.core.ChatStore;
import com.wrist.deepseek.core.Conversation;
import com.wrist.deepseek.core.DeepSeekClient;
import com.wrist.deepseek.core.Message;
import com.wrist.deepseek.core.Prompts;
import com.wrist.deepseek.core.SearchClient;
import com.wrist.deepseek.core.SearchResult;
import com.wrist.deepseek.core.Settings;
import com.wrist.deepseek.core.TranscriptFixer;
import com.wrist.deepseek.util.Haptics;
import com.wrist.deepseek.util.Markdown;
import com.wrist.deepseek.util.Ui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends ScaledActivity {
    private static final int REQ_AUDIO = 71;
    private static final int REQ_HISTORY = 72;
    private static final int REQ_SETTINGS = 73;

    private App app;
    private Settings settings;
    private ChatStore store;

    private FrameLayout root;
    private LinearLayout contentView;
    private ScrollView scrollView;
    private LinearLayout messageList;
    private LinearLayout emptyView;
    private TextView emptyTitle;
    private TextView emptySub;
    private TextView emptyCta;
    private TextView emptyHint;
    private LinearLayout suggestions;
    private View titleDot;
    private TextView titleView;
    private LinearLayout topLeftBox;
    private TextView clockView;
    private TextView holdHintView;
    private IconView scrollDown;
    private boolean privateMode;
    private boolean newChatAnimating;

    private EditText input;
    private IconView actionButton;
    private IconView searchButton;
    private VoiceOverlay voice;
    private HoldVoiceOverlay holdVoice;
    private boolean holdUiMode;
    private boolean voiceAutoSend;
    private int pendingVoiceMode;
    private boolean holdCandidate;
    private boolean holdTriggered;
    private boolean holdCancelArmed;
    private float holdDownX;
    private float holdDownY;

    private long conversationId;
    private boolean streaming;
    private long streamingMsgId = -1;
    private Message streamingMsg;
    private MessageViewHolder streamingHolder;
    private String streamContent = "";
    private String streamReasoning = "";
    private boolean streamUiScheduled;
    private long lastDbWrite;
    private DeepSeekClient activeClient;
    private boolean autoScroll = true;
    private boolean userTouching;
    private int dotPhase;
    private boolean destroyed;
    private final Runnable clockTicker = new Runnable() {
        @Override
        public void run() {
            updateClock();
            if (!destroyed) App.MAIN.postDelayed(this, 15000);
        }
    };
    private final Runnable holdStartRunnable = () -> {
        if (!holdCandidate || streaming) return;
        holdCandidate = false;
        holdTriggered = true;
        beginHoldVoice();
    };

    private AudioRecorder recorder;
    private int voiceSession;
    private boolean voiceBusy;
    private final TranscriptFixer fixer = new TranscriptFixer();

    private TextToSpeech tts;
    private boolean ttsReady;

    private final Map<Long, MessageViewHolder> holders = new HashMap<>();

    private final Runnable streamRender = new Runnable() {
        @Override
        public void run() {
            streamUiScheduled = false;
            if (!streaming || streamingMsg == null) return;
            streamingMsg.content = streamContent;
            streamingMsg.reasoning = streamReasoning;
            if (streamingHolder != null) streamingHolder.bind(streamingMsg, true);
            long now = System.currentTimeMillis();
            if (now - lastDbWrite > 800) {
                lastDbWrite = now;
                store.updateMessage(streamingMsg.id, streamContent, streamReasoning, 1);
            }
            if (autoScroll) scrollToBottom();
        }
    };

    private final Runnable dotsTicker = new Runnable() {
        @Override
        public void run() {
            if (destroyed) return;
            if (streaming && TextUtils.isEmpty(streamContent) && streamingHolder != null && streamingMsg != null) {
                dotPhase = (dotPhase + 1) % 4;
                streamingHolder.bind(streamingMsg, true);
            }
            App.MAIN.postDelayed(this, 480);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = App.get(this);
        settings = app.settings;
        store = app.store;
        setupWindow();
        buildUi();
        initTts();

        long last = settings.getLastConversation();
        if (last > 0 && store.conversationExists(last)) {
            loadConversation(last, false);
        } else {
            conversationId = 0;
            renderHistory();
        }
        String draft = settings.getDraft();
        if (!TextUtils.isEmpty(draft)) {
            input.setText(draft);
            input.setSelection(input.length());
        }
        updateComposer();
        refreshTop();
        App.MAIN.post(dotsTicker);
        App.MAIN.postDelayed(this::showHoldHintOnce, 700);
    }

    private void showHoldHintOnce() {
        if (destroyed || holdHintView == null || !settings.hasDeepSeekKey()
                || settings.isHoldHintShown()) return;
        settings.setHoldHintShown(true);
        holdHintView.setVisibility(View.VISIBLE);
        holdHintView.setAlpha(0f);
        holdHintView.animate().alpha(1f).setDuration(180).start();
        App.MAIN.postDelayed(() -> {
            if (holdHintView == null) return;
            holdHintView.animate().alpha(0f).setDuration(220).withEndAction(() -> {
                if (holdHintView != null) holdHintView.setVisibility(View.GONE);
            }).start();
        }, 4200);
    }

    private void hideHoldHint() {
        if (holdHintView == null || holdHintView.getVisibility() != View.VISIBLE) return;
        holdHintView.animate().cancel();
        holdHintView.setVisibility(View.GONE);
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
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        contentView = new LinearLayout(this);
        contentView.setOrientation(LinearLayout.VERTICAL);
        root.addView(contentView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        contentView.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        contentView.addView(buildTopBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 74)));

        FrameLayout chatArea = new FrameLayout(this);
        LinearLayout.LayoutParams chatLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        contentView.addView(chatArea, chatLp);

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setClipToPadding(false);
        chatArea.addView(scrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        messageList = new LinearLayout(this);
        messageList.setOrientation(LinearLayout.VERTICAL);
        messageList.setPadding(Ui.dp(this, 12), Ui.dp(this, 5), Ui.dp(this, 12), Ui.dp(this, 10));
        scrollView.addView(messageList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        emptyView = buildEmptyView();
        chatArea.addView(emptyView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        scrollDown = new IconView(this, IconView.ARROW_DOWN, Ui.MUTED, 31);
        scrollDown.setBackground(circleRipple(0xFF1D1D25, 16));
        scrollDown.setVisibility(View.GONE);
        scrollDown.setOnClickListener(v -> {
            autoScroll = true;
            scrollToBottom();
        });
        FrameLayout.LayoutParams sdlp = new FrameLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32));
        sdlp.gravity = Gravity.END | Gravity.BOTTOM;
        sdlp.rightMargin = Ui.dp(this, 14);
        sdlp.bottomMargin = Ui.dp(this, 4);
        chatArea.addView(scrollDown, sdlp);

        scrollView.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                userTouching = true;
            } else if (event.getAction() == android.view.MotionEvent.ACTION_UP
                    || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                userTouching = false;
                autoScroll = isAtBottom();
                updateScrollDown();
            }
            return false;
        });
        scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldX, oldY) -> {
            if (!userTouching && isAtBottom()) autoScroll = true;
            updateScrollDown();
        });

        contentView.addView(buildComposer(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        holdHintView = Ui.mediumText(this, "点击输入 · 按住说话", 10.5f, Ui.BLUE_LIGHT);
        holdHintView.setGravity(Gravity.CENTER);
        holdHintView.setPadding(Ui.dp(this, 12), Ui.dp(this, 7),
                Ui.dp(this, 12), Ui.dp(this, 7));
        holdHintView.setBackground(Ui.roundStroke(0xF214141A, 12, 0xFF30303B, 1, this));
        holdHintView.setElevation(Ui.dp(this, 6));
        holdHintView.setVisibility(View.GONE);
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        hintLp.bottomMargin = Ui.dp(this, 76);
        root.addView(holdHintView, hintLp);

        voice = new VoiceOverlay(this, new VoiceOverlay.Listener() {
            @Override
            public void onStop() {
                stopVoiceRecording();
            }

            @Override
            public void onCancel() {
                cancelVoice();
            }

            @Override
            public void onRetry() {
                retryVoice();
            }

            @Override
            public void onWhisperChanged(boolean whisper) {
                settings.setWhisperMode(whisper);
                Ui.toast(MainActivity.this, whisper ? "悄悄话增强已开启" : "悄悄话增强已关闭");
            }
        });
        root.addView(voice, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        holdVoice = new HoldVoiceOverlay(this);
        root.addView(holdVoice, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        root.requestApplyInsets();
        App.MAIN.post(clockTicker);
        if (!settings.isScaleConfigured()) {
            App.MAIN.postDelayed(() -> {
                if (!destroyed) startActivity(new android.content.Intent(this, ScaleActivity.class));
            }, 350);
        } else if (!settings.isTutorialShown()) {
            settings.setTutorialShown(true);
            App.MAIN.postDelayed(() -> {
                if (!destroyed) startActivity(new android.content.Intent(this, TutorialActivity.class));
            }, 650);
        }
    }

    private String clockText() {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
    }

    private void updateClock() {
        if (clockView != null) clockView.setText(clockText());
    }

    private void applyClockVisibility() {
        boolean show = settings.isShowClock();
        if (clockView != null) clockView.setVisibility(show ? View.VISIBLE : View.GONE);
        if (topLeftBox != null) {
            topLeftBox.setGravity(show ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER);
        }
    }

    private View buildTopBar() {
        FrameLayout bar = new FrameLayout(this);

        LinearLayout leftBox = new LinearLayout(this);
        leftBox.setOrientation(LinearLayout.VERTICAL);
        leftBox.setGravity(Gravity.CENTER_HORIZONTAL);
        leftBox.setPadding(0, Ui.dp(this, 4), 0, 0);
        topLeftBox = leftBox;
        clockView = Ui.mediumText(this, clockText(), 21, Ui.TEXT);
        clockView.setGravity(Gravity.CENTER);
        clockView.setLetterSpacing(0.02f);
        leftBox.addView(clockView);

        IconView menu = new IconView(this, IconView.MENU, Ui.TEXT, 30);
        menu.setBackground(circleRipple(0x1FFFFFFF, 15));
        menu.setContentDescription("菜单");
        menu.setOnClickListener(v -> openMenu());
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                Ui.dp(this, 32), Ui.dp(this, 32));
        mlp.topMargin = Ui.dp(this, 1);
        leftBox.addView(menu, mlp);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(
                Ui.dp(this, 64), Ui.dp(this, 66));
        llp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        llp.leftMargin = Ui.dp(this, 18);
        bar.addView(leftBox, llp);
        applyClockVisibility();

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.HORIZONTAL);
        center.setGravity(Gravity.CENTER);
        center.setClickable(true);
        center.setBackground(circleRipple(0x14FFFFFF, 14));
        titleView = Ui.mediumText(this, "DeepSeek", 15.5f, Ui.TEXT);
        center.addView(titleView);
        titleDot = new View(this);
        GradientDrawable dotBg = Ui.round(Ui.ACCENT, 3, this);
        titleDot.setBackground(dotBg);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(Ui.dp(this, 6), Ui.dp(this, 6));
        dotLp.leftMargin = Ui.dp(this, 5);
        center.addView(titleDot, dotLp);
        center.setOnClickListener(v -> showModelSheet());
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 34));
        clp.gravity = Gravity.CENTER;
        bar.addView(center, clp);

        IconView plus = new IconView(this, IconView.PLUS, Ui.TEXT, 34);
        plus.setBackground(circleRipple(0x1FFFFFFF, 17));
        plus.setContentDescription("新对话");
        plus.setOnClickListener(v -> newChat());
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36));
        plp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        plp.rightMargin = Ui.dp(this, 6);
        bar.addView(plus, plp);
        return bar;
    }

    private LinearLayout buildEmptyView() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 8));
        box.setClickable(false);

        emptyTitle = Ui.mediumText(this, "你好，我是 DeepSeek", 20, Ui.TEXT);
        emptyTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        box.addView(emptyTitle, tlp);

        emptySub = Ui.text(this, "今天想聊点什么？", 13, Ui.MUTED);
        emptySub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(this, 6);
        box.addView(emptySub, slp);

        suggestions = new LinearLayout(this);
        suggestions.setOrientation(LinearLayout.HORIZONTAL);
        suggestions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sugLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sugLp.topMargin = Ui.dp(this, 20);
        box.addView(suggestions, sugLp);

        addSuggestion("解释量子纠缠", "用一句话解释量子纠缠");
        addSuggestion("写一段周报", "帮我写一段本周工作周报");

        emptyCta = Ui.mediumText(this, "配置 API Key", 14, Color.WHITE);
        emptyCta.setGravity(Gravity.CENTER);
        emptyCta.setBackground(circleRippleColor(Ui.ACCENT, 20));
        emptyCta.setClickable(true);
        emptyCta.setOnClickListener(v -> openSettings("deepseek"));
        LinearLayout.LayoutParams ctaLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 42));
        ctaLp.topMargin = Ui.dp(this, 18);
        box.addView(emptyCta, ctaLp);

        emptyHint = Ui.text(this, "轻点麦克风 · 悄悄话也能听清", 10, Ui.DIM);
        emptyHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = Ui.dp(this, 22);
        box.addView(emptyHint, hlp);

        return box;
    }

    private void addSuggestion(String label, String prompt) {
        TextView chip = Ui.mediumText(this, label, 11.5f, Ui.TEXT);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF),
                Ui.roundStroke(0xFF17171D, 16, Ui.BORDER, 1, this),
                Ui.round(Color.WHITE, 16, this)));
        chip.setClickable(true);
        chip.setOnClickListener(v -> sendMessage(prompt));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1);
        lp.rightMargin = Ui.dp(this, 5);
        if (suggestions.getChildCount() > 0) lp.leftMargin = Ui.dp(this, 5);
        suggestions.addView(chip, lp);
    }

    private View buildComposer() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.HORIZONTAL);
        outer.setGravity(Gravity.CENTER_VERTICAL);
        outer.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 6));

        FrameLayout pill = new FrameLayout(this);
        GradientDrawable shell = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF05080D, 0xFF0B1830});
        shell.setCornerRadius(Ui.dp(this, 31));
        shell.setStroke(Math.max(1, Ui.dp(this, 1)), 0xFF2B3D59);
        pill.setBackground(shell);
        pill.setElevation(Ui.dp(this, 8));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 6), Ui.dp(this, 7), Ui.dp(this, 5), Ui.dp(this, 7));

        View rim = new View(this);
        GradientDrawable rimBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x003B76FF, 0xAA3B76FF, 0x003B76FF});
        rim.setBackground(rimBg);
        FrameLayout.LayoutParams rimp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1.5f));
        rimp.gravity = Gravity.BOTTOM;
        rimp.leftMargin = Ui.dp(this, 26);
        rimp.rightMargin = Ui.dp(this, 26);
        rimp.bottomMargin = Ui.dp(this, 2);
        pill.addView(rim, rimp);

        pill.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        searchButton = new IconView(this, IconView.GLOBE, 0xFFD3D9E6, 19);
        searchButton.setBackground(circleRipple(0x1FFFFFFF, 20));
        searchButton.setOnClickListener(v -> toggleSearch());
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                Ui.dp(this, 38), Ui.dp(this, 38));
        searchLp.leftMargin = Ui.dp(this, 1);
        row.addView(searchButton, searchLp);

        View divider = new View(this);
        divider.setBackgroundColor(0xFF2A364A);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                Math.max(1, Ui.dp(this, 1)), Ui.dp(this, 26));
        dlp.leftMargin = Ui.dp(this, 9);
        dlp.rightMargin = Ui.dp(this, 2);
        row.addView(divider, dlp);

        input = new EditText(this);
        input.setHint("给 DeepSeek 发消息");
        input.setHintTextColor(0xFF7F8BA0);
        input.setTextColor(0xFFE8EBF3);
        input.setTextSize(14.5f);
        input.setTypeface(Ui.regular());
        input.setBackground(null);
        input.setPadding(Ui.dp(this, 9), 0, Ui.dp(this, 4), 0);
        input.setMaxLines(3);
        input.setSingleLine(false);
        input.setFocusable(true);
        input.setFocusableInTouchMode(true);
        input.setMinHeight(Ui.dp(this, 40));
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setIncludeFontPadding(false);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateComposer();
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN)) {
                if (!streaming) {
                    sendMessage(null);
                    return true;
                }
            }
            return false;
        });
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                autoScroll = true;
                scrollToBottom();
            }
        });
        input.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                hideHoldHint();
                if (streaming || input.getText().length() > 0) return false;
                holdCandidate = true;
                holdTriggered = false;
                holdCancelArmed = false;
                holdDownX = event.getX();
                holdDownY = event.getY();
                App.MAIN.removeCallbacks(holdStartRunnable);
                App.MAIN.postDelayed(holdStartRunnable, 260);
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (holdCandidate && !holdTriggered
                        && (Math.abs(event.getX() - holdDownX) > Ui.dp(this, 22)
                        || Math.abs(event.getY() - holdDownY) > Ui.dp(this, 22))) {
                    cancelHoldCandidate();
                } else if (holdTriggered && holdVoice != null) {
                    float slideUp = holdDownY - event.getY();
                    boolean armed = slideUp > Ui.dp(this, 62);
                    if (armed != holdCancelArmed && slideUp > Ui.dp(this, 34)) {
                        holdCancelArmed = armed;
                        holdVoice.setCancelArmed(armed);
                        Haptics.soft(this);
                    }
                }
            } else if (action == MotionEvent.ACTION_UP) {
                App.MAIN.removeCallbacks(holdStartRunnable);
                holdCandidate = false;
                if (holdTriggered) {
                    holdTriggered = false;
                    if (holdCancelArmed) {
                        holdCancelArmed = false;
                        cancelVoice();
                        Ui.toast(this, "已取消");
                        Haptics.soft(this);
                    } else {
                        stopVoiceRecording();
                    }
                }
            } else if (action == MotionEvent.ACTION_CANCEL) {
                App.MAIN.removeCallbacks(holdStartRunnable);
                holdCandidate = false;
                holdCancelArmed = false;
                if (holdTriggered) {
                    holdTriggered = false;
                    cancelVoice();
                }
            }
            return false;
        });
        input.setOnClickListener(v -> {
            input.requestFocus();
            showKeyboard();
        });
        row.setOnClickListener(v -> {
            input.requestFocus();
            showKeyboard();
        });
        row.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        actionButton = new IconView(this, IconView.MIC, 0xFFDDEBFF, 21);
        actionButton.setOnClickListener(v -> {
            if (streaming) {
                stopStreaming();
            } else if (!TextUtils.isEmpty(input.getText().toString().trim())) {
                sendMessage(null);
            } else {
                startVoiceFlow();
            }
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                Ui.dp(this, 38), Ui.dp(this, 38));
        alp.rightMargin = Ui.dp(this, 4);
        row.addView(actionButton, alp);

        outer.addView(pill, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return outer;
    }

    private void updateComposer() {
        if (searchButton != null) {
            boolean on = settings.isSearchEnabled();
            searchButton.setColor(on ? 0xFF3F6DB8 : 0xFFE6ECF7);
            GradientDrawable searchBg = Ui.roundStroke(
                    on ? 0xFF101D33 : 0xFF0B111C, 20,
                    on ? 0xFF2B4468 : 0xFF2A364A, 1, this);
            searchButton.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(0x33FFFFFF), searchBg,
                    Ui.round(Color.WHITE, 20, this)));
            searchButton.setContentDescription(on ? "联网搜索已开启" : "联网搜索已关闭");
        }
        if (actionButton == null) return;
        int icon;
        int top;
        int bottom;
        if (streaming) {
            icon = IconView.STOP;
            top = 0xFF4B7DFF;
            bottom = 0xFF1D3EA8;
            actionButton.setContentDescription("停止生成");
        } else if (!TextUtils.isEmpty(input.getText().toString().trim())) {
            icon = IconView.SEND;
            top = 0xFF4A86FF;
            bottom = 0xFF1E4FD8;
            actionButton.setContentDescription("发送");
        } else {
            icon = IconView.MIC;
            // Frosted, translucent deep navy instead of the bright blue disc.
            top = 0xE61A2B48;
            bottom = 0xD90B1424;
            actionButton.setContentDescription("语音输入");
        }
        actionButton.setIcon(icon);
        actionButton.setColor(icon == IconView.MIC ? 0xFFDDEBFF : Color.WHITE);
        GradientDrawable content = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{top, bottom});
        content.setShape(GradientDrawable.OVAL);
        actionButton.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x33FFFFFF), content, Ui.round(Color.WHITE, 19, this)));
    }

    private void toggleSearch() {
        boolean next = !settings.isSearchEnabled();
        settings.setSearchEnabled(next);
        updateComposer();
        Ui.haptic(this, 10);
        if (next) {
            Ui.toast(this, "联网搜索已开启，下一条消息会先搜索网页");
        } else {
            Ui.toast(this, "联网搜索已关闭");
        }
    }

    private void initTts() {
        try {
            tts = new TextToSpeech(getApplicationContext(), status -> {
                ttsReady = status == TextToSpeech.SUCCESS;
                if (ttsReady) {
                    try {
                        int r = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                            ttsReady = false;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
            ttsReady = false;
        }
    }

    private void speak(String text) {
        if (!ttsReady || tts == null) {
            Ui.toast(this, "手表未安装可用的中文语音引擎");
            return;
        }
        String plain = Ui.stripMarkdown(text);
        if (plain.isEmpty()) return;
        try {
            tts.speak(plain, TextToSpeech.QUEUE_FLUSH, null, "ds-" + System.currentTimeMillis());
        } catch (Throwable t) {
            Ui.toast(this, "朗读失败");
        }
    }

    private void refreshTop() {
        if (titleView != null) {
            titleView.setText(privateMode ? "隐私空间" : "DeepSeek");
            titleView.setTextColor(privateMode ? Ui.BLUE_LIGHT : Ui.TEXT);
        }
        if (titleDot != null) {
            titleDot.setVisibility(settings.isThinking() ? View.VISIBLE : View.GONE);
        }
    }

    private void refreshWelcome() {
        if (emptyTitle == null) return;
        boolean hasKey = settings.hasDeepSeekKey();
        if (hasKey) {
            if (privateMode) {
                emptyTitle.setText("隐私空间");
                emptySub.setText("这里的对话只在隐私空间显示");
                emptyHint.setText("退出后，普通历史看不到这些内容");
            } else {
                emptyTitle.setText("你好，我是 DeepSeek");
                emptySub.setText("今天想聊点什么？");
                emptyHint.setText("轻点麦克风 · 悄悄话也能听清");
            }
            suggestions.setVisibility(View.VISIBLE);
            emptyCta.setVisibility(View.GONE);
        } else {
            emptyTitle.setText("连接 DeepSeek");
            emptySub.setText("填入 API Key 即可开始，记录只保存在手表");
            suggestions.setVisibility(View.GONE);
            emptyCta.setVisibility(View.VISIBLE);
            emptyHint.setText("点左上角菜单 → 设置，可用手机配置");
        }
        refreshTop();
    }

    private void updateEmpty() {
        if (emptyView == null) return;
        boolean empty = messageList.getChildCount() == 0;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) refreshWelcome();
    }

    private void renderHistory() {
        messageList.removeAllViews();
        holders.clear();
        streamingHolder = null;
        if (conversationId <= 0) {
            updateEmpty();
            return;
        }
        List<Message> messages = store.messages(conversationId);
        for (Message m : messages) {
            MessageViewHolder h = new MessageViewHolder(m);
            messageList.addView(h.wrapper);
            holders.put(m.id, h);
        }
        updateEmpty();
        if (autoScroll) scrollToBottom();
    }

    private void loadConversation(long id, boolean animate) {
        Conversation c = store.getConversation(id);
        if (c == null || c.isPrivate != privateMode) {
            startEmptySpace();
            return;
        }
        abortStream();
        conversationId = id;
        if (privateMode) settings.setPrivacyLastConversation(id);
        else settings.setLastConversation(id);
        autoScroll = true;
        renderHistory();
        if (animate) scrollToBottom();
    }

    private void startEmptySpace() {
        abortStream();
        conversationId = 0;
        input.setText("");
        messageList.removeAllViews();
        holders.clear();
        updateEmpty();
        updateComposer();
        hideKeyboard();
        autoScroll = true;
        scrollView.scrollTo(0, 0);
        refreshWelcome();
    }

    private void newChat() {
        if (destroyed || newChatAnimating) return;
        if (scrollView == null) {
            performNewChat();
            return;
        }
        newChatAnimating = true;
        scrollView.animate().cancel();
        scrollView.animate().alpha(0f).translationY(-Ui.dp(this, 10))
                .setDuration(150)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    performNewChat();
                    if (destroyed) {
                        newChatAnimating = false;
                        return;
                    }
                    scrollView.setAlpha(0f);
                    scrollView.setTranslationY(Ui.dp(this, 14));
                    scrollView.animate().alpha(1f).translationY(0f)
                            .setDuration(260)
                            .setInterpolator(new DecelerateInterpolator(1.8f))
                            .withEndAction(() -> newChatAnimating = false)
                            .start();
                }).start();
    }

    private void performNewChat() {
        if (privateMode) settings.setPrivacyLastConversation(0);
        else settings.setLastConversation(0);
        settings.setDraft("");
        startEmptySpace();
    }

    private void openMenu() {
        List<Widgets.Item> items = new ArrayList<>();
        items.add(new Widgets.Item("新对话", IconView.PLUS));
        items.add(new Widgets.Item(privateMode ? "隐私对话" : "对话历史", IconView.HISTORY));
        items.add(new Widgets.Item(privateMode ? "退出隐私空间" : "隐私空间", IconView.LOCK,
                privateMode ? Ui.ERROR : Ui.BLUE_LIGHT, false));
        items.add(new Widgets.Item("设置", IconView.SETTINGS));
        Widgets.OnItem onItem = index -> {
            if (index == 0) newChat();
            else if (index == 1) openHistory();
            else if (index == 2) {
                if (privateMode) exitPrivacy();
                else openPrivacy();
            } else openSettings(null);
        };
        if (settings.isSideMenu()) {
            Widgets.showSideMenu(this, privateMode ? "隐私空间" : "菜单", items, onItem);
        } else {
            Widgets.showSheet(this, null, items, onItem);
        }
    }

    private void openPrivacy() {
        if (privateMode) {
            exitPrivacy();
            return;
        }
        PinDialog.show(this, settings, !settings.hasPrivacyPin(), this::enterPrivacy);
    }

    private void enterPrivacy() {
        privateMode = true;
        settings.setDraft("");
        refreshTop();
        long last = settings.getPrivacyLastConversation();
        if (last > 0 && store.isPrivateConversation(last)) loadConversation(last, true);
        else startEmptySpace();
        Ui.toast(this, "已进入隐私空间");
    }

    private void exitPrivacy() {
        privateMode = false;
        settings.setDraft("");
        refreshTop();
        long last = settings.getLastConversation();
        if (last > 0 && store.conversationExists(last) && !store.isPrivateConversation(last)) {
            loadConversation(last, true);
        } else {
            startEmptySpace();
        }
        Ui.toast(this, "已退出隐私空间");
    }

    private void openHistory() {
        Intent i = new Intent(this, HistoryActivity.class);
        i.putExtra("current_id", conversationId);
        i.putExtra("privacy", privateMode);
        startActivityForResult(i, REQ_HISTORY);
    }

    private void openSettings(String focus) {
        Intent i = new Intent(this, SettingsActivity.class);
        if (focus != null) i.putExtra("focus", focus);
        startActivityForResult(i, REQ_SETTINGS);
    }

    private void showModelSheet() {
        List<Widgets.Item> items = new ArrayList<>();
        items.add(new Widgets.Item("快速对话", settings.isThinking() ? IconView.SPARK : IconView.CHECK,
                settings.isThinking() ? Ui.MUTED : Ui.ACCENT, false));
        items.add(new Widgets.Item("深度思考 V4", settings.isThinking() ? IconView.CHECK : IconView.SPARK,
                settings.isThinking() ? Ui.ACCENT : Ui.MUTED, false));
        items.add(new Widgets.Item(settings.isSearchEnabled() ? "联网搜索 · 已开" : "联网搜索 · 已关",
                IconView.GLOBE, settings.isSearchEnabled() ? Ui.ACCENT : Ui.MUTED, false));
        Widgets.showSheet(this, "选择回答方式", items, index -> {
            if (index == 2) {
                toggleSearch();
                return;
            }
            boolean think = index == 1;
            if (think == settings.isThinking()) return;
            settings.setThinking(think);
            refreshTop();
            Ui.toast(this, think ? "下一条将使用深度思考" : "已切换到快速对话");
        });
    }

    private void sendMessage(String forced) {
        if (!settings.hasDeepSeekKey()) {
            promptDeepSeekKey();
            return;
        }
        if (streaming) {
            Ui.toast(this, "正在生成，请先停止");
            return;
        }
        String text = forced != null ? forced : input.getText().toString();
        text = text == null ? "" : text.trim();
        if (text.isEmpty()) return;
        hideKeyboard();
        if (conversationId <= 0) {
            conversationId = store.createConversation(Ui.titleFrom(text), privateMode);
            if (privateMode) settings.setPrivacyLastConversation(conversationId);
            else settings.setLastConversation(conversationId);
        }
        Message user = new Message("user", text);
        user.conversationId = conversationId;
        user.createdAt = System.currentTimeMillis();
        user.id = store.addMessage(conversationId, "user", text, "", 0);
        MessageViewHolder h = new MessageViewHolder(user);
        messageList.addView(h.wrapper);
        holders.put(user.id, h);
        input.setText("");
        settings.setDraft("");
        updateEmpty();
        updateComposer();
        autoScroll = true;
        scrollToBottom();
        generateAssistant();
    }

    private void generateAssistant() {
        if (streaming) return;
        if (!settings.hasDeepSeekKey()) {
            promptDeepSeekKey();
            return;
        }
        if (conversationId <= 0) return;

        Message a = new Message("assistant", "");
        a.conversationId = conversationId;
        a.createdAt = System.currentTimeMillis();
        a.status = 1;
        a.searching = settings.isSearchEnabled();
        a.id = store.addMessage(conversationId, "assistant", "", "", 1);
        streaming = true;
        streamingMsg = a;
        streamingMsgId = a.id;
        streamContent = "";
        streamReasoning = "";
        streamUiScheduled = false;
        lastDbWrite = 0;
        streamingHolder = new MessageViewHolder(a);
        messageList.addView(streamingHolder.wrapper);
        holders.put(a.id, streamingHolder);
        updateEmpty();
        updateComposer();
        autoScroll = true;
        scrollToBottom();

        if (a.searching) {
            runWebSearchAndAnswer(a);
        } else {
            startChatStream(a, Prompts.chatSystem(settings));
        }
    }

    private void runWebSearchAndAnswer(Message a) {
        String question = store.lastUserText(conversationId);
        final long msgId = a.id;
        if (TextUtils.isEmpty(question)) {
            a.searching = false;
            startChatStream(a, Prompts.chatSystem(settings));
            return;
        }
        App.IO.execute(() -> new SearchClient().search(settings, question, 5, new SearchClient.Callback() {
            @Override
            public void onResults(List<SearchResult> results) {
                App.MAIN.post(() -> {
                    if (!streaming || streamingMsgId != msgId) return;
                    a.searching = false;
                    a.sources = SearchResult.toJsonArray(results);
                    store.updateSources(a.id, a.sources);
                    if (results == null || results.isEmpty()) {
                        a.searchNote = "没有检索到相关网页";
                    }
                    MessageViewHolder h = holders.get(a.id);
                    if (h != null) h.bind(a, true);
                    startChatStream(a, Prompts.chatSystemWithSearch(settings, results));
                });
            }

            @Override
            public void onError(String message) {
                App.MAIN.post(() -> {
                    if (!streaming || streamingMsgId != msgId) return;
                    a.searching = false;
                    a.searchNote = "搜索失败，改为直接回答";
                    MessageViewHolder h = holders.get(a.id);
                    if (h != null) h.bind(a, true);
                    Ui.toast(MainActivity.this, message);
                    startChatStream(a, Prompts.chatSystem(settings));
                });
            }
        }));
    }

    private void startChatStream(Message a, String systemPrompt) {
        if (!streaming || streamingMsgId != a.id) return;
        List<Message> context = buildContext(a.id);
        String model = settings.getModel();
        boolean thinking = settings.isThinking();
        activeClient = new DeepSeekClient();
        final long msgId = a.id;
        final DeepSeekClient client = activeClient;
        App.CHAT.execute(() -> client.stream(settings, systemPrompt, context, model, thinking,
                new DeepSeekClient.StreamCallback() {
                    @Override
                    public void onDelta(String contentSoFar, String reasoningSoFar) {
                        App.MAIN.post(() -> {
                            if (!streaming || streamingMsgId != msgId) return;
                            streamContent = contentSoFar;
                            streamReasoning = reasoningSoFar;
                            if (!streamUiScheduled) {
                                streamUiScheduled = true;
                                App.MAIN.postDelayed(streamRender, 45);
                            }
                        });
                    }

                    @Override
                    public void onDone(String content, String reasoning, String finishReason) {
                        App.MAIN.post(() -> finishStream(msgId, content, reasoning));
                    }

                    @Override
                    public void onError(String message) {
                        App.MAIN.post(() -> failStream(msgId, message));
                    }
                }));
    }

    private List<Message> buildContext(long placeholderId) {
        List<Message> all = store.messages(conversationId);
        List<Message> ctx = new ArrayList<>();
        int chars = 0;
        for (int i = all.size() - 1; i >= 0; i--) {
            Message m = all.get(i);
            if (m.id == placeholderId) continue;
            if (m.content == null || m.content.trim().isEmpty()) continue;
            int len = m.content.length();
            if (chars + len > 24000 && !ctx.isEmpty()) break;
            ctx.add(0, m);
            chars += len;
        }
        return ctx;
    }

    private void finishStream(long msgId, String content, String reasoning) {
        if (!streaming || streamingMsgId != msgId) return;
        App.MAIN.removeCallbacks(streamRender);
        streamUiScheduled = false;
        Message a = streamingMsg;
        String finalContent = content == null ? "" : content;
        String finalReasoning = reasoning == null ? "" : reasoning;
        if (a != null) {
            a.content = finalContent;
            a.reasoning = finalReasoning;
            a.status = TextUtils.isEmpty(finalContent) ? 2 : 0;
            store.updateMessage(a.id, a.content, a.reasoning, a.status);
            MessageViewHolder h = holders.get(a.id);
            if (h != null) h.bind(a, false);
        }
        streaming = false;
        streamingMsgId = -1;
        streamingMsg = null;
        streamingHolder = null;
        streamContent = "";
        streamReasoning = "";
        activeClient = null;
        updateComposer();
        if (autoScroll) scrollToBottom();
        if (a != null && a.status == 0 && settings.isTtsEnabled()) {
            speak(a.content);
        }
    }

    private void failStream(long msgId, String message) {
        if (!streaming || streamingMsgId != msgId) return;
        App.MAIN.removeCallbacks(streamRender);
        streamUiScheduled = false;
        Message a = streamingMsg;
        if (a != null) {
            a.content = streamContent;
            a.reasoning = streamReasoning;
            a.status = 2;
            store.updateMessage(a.id, a.content, a.reasoning, 2);
            MessageViewHolder h = holders.get(a.id);
            if (h != null) h.bind(a, false);
        }
        streaming = false;
        streamingMsgId = -1;
        streamingMsg = null;
        streamingHolder = null;
        activeClient = null;
        updateComposer();
        Ui.toast(this, message == null ? "生成失败，请重试" : message);
    }

    private void stopStreaming() {
        if (!streaming) return;
        DeepSeekClient c = activeClient;
        if (c != null) c.cancel();
        App.MAIN.removeCallbacks(streamRender);
        streamUiScheduled = false;
        if (streamingMsg != null) {
            streamingMsg.content = streamContent;
            streamingMsg.reasoning = streamReasoning;
            streamingMsg.status = 3;
            store.updateMessage(streamingMsg.id, streamContent, streamReasoning, 3);
            MessageViewHolder h = holders.get(streamingMsg.id);
            if (h != null) h.bind(streamingMsg, false);
        }
        streaming = false;
        streamingMsgId = -1;
        streamingMsg = null;
        streamingHolder = null;
        activeClient = null;
        updateComposer();
        Ui.toast(this, "已停止生成");
    }

    private void abortStream() {
        if (activeClient != null) activeClient.cancel();
        App.MAIN.removeCallbacks(streamRender);
        streamUiScheduled = false;
        if (streaming && streamingMsg != null) {
            streamingMsg.content = streamContent;
            streamingMsg.reasoning = streamReasoning;
            streamingMsg.status = 3;
            store.updateMessage(streamingMsg.id, streamContent, streamReasoning, 3);
        }
        streaming = false;
        streamingMsgId = -1;
        streamingMsg = null;
        streamingHolder = null;
        activeClient = null;
        updateComposer();
    }

    private void retryMessage(Message m) {
        if (m == null || m.isUser()) return;
        if (streaming) {
            Ui.toast(this, "请先停止当前生成");
            return;
        }
        store.deleteMessageAndAfter(conversationId, m.id);
        renderHistory();
        updateEmpty();
        autoScroll = true;
        generateAssistant();
    }

    private void editUserMessage(Message m) {
        if (m == null || !m.isUser()) return;
        Widgets.confirm(this, "编辑这条消息？", "发送时会替换这条消息以及它之后的对话。", "继续", () -> {
            store.deleteMessageAndAfter(conversationId, m.id);
            renderHistory();
            input.setText(m.content);
            input.setSelection(input.length());
            input.requestFocus();
            showKeyboard();
            updateComposer();
        });
    }

    private void deleteFromMessage(Message m) {
        if (m == null) return;
        Widgets.confirm(this, "删除这段对话？", "这条消息以及它之后的对话都会被删除，此操作无法撤销。", "删除", () -> {
            store.deleteMessageAndAfter(conversationId, m.id);
            renderHistory();
            updateEmpty();
        });
    }

    private void showMessageActions(Message m) {
        List<Widgets.Item> items = new ArrayList<>();
        items.add(new Widgets.Item("复制", IconView.COPY));
        if (m.isAssistant() && !TextUtils.isEmpty(m.content)) {
            items.add(new Widgets.Item("朗读", IconView.SPEAKER));
            items.add(new Widgets.Item("重新生成", IconView.REFRESH));
        }
        if (m.isUser()) {
            items.add(new Widgets.Item("编辑重发", IconView.EDIT));
        }
        items.add(new Widgets.Item("删除", IconView.TRASH, Ui.ERROR, true));
        Widgets.showSheet(this, null, items, index -> {
            Widgets.Item it = items.get(index);
            if ("复制".equals(it.label)) {
                String copy = m.content;
                if (TextUtils.isEmpty(copy)) copy = m.reasoning;
                Ui.copy(this, copy);
            } else if ("朗读".equals(it.label)) {
                speak(m.content);
            } else if ("重新生成".equals(it.label)) {
                retryMessage(m);
            } else if ("编辑重发".equals(it.label)) {
                editUserMessage(m);
            } else if ("删除".equals(it.label)) {
                deleteFromMessage(m);
            }
        });
    }

    private void showSources(List<SearchResult> sources) {
        if (sources == null || sources.isEmpty()) return;
        List<Widgets.Item> items = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            SearchResult r = sources.get(i);
            String label = (i + 1) + ". " + Ui.oneLine(r.title, 24);
            items.add(new Widgets.Item(label, IconView.GLOBE));
        }
        Widgets.showSheet(this, "来源 · 点击复制链接", items, index -> {
            SearchResult r = sources.get(index);
            Ui.copy(this, TextUtils.isEmpty(r.url) ? r.title : r.url);
        });
    }

    private void promptDeepSeekKey() {
        List<Widgets.Item> items = new ArrayList<>();
        items.add(new Widgets.Item("填写 API Key", IconView.EDIT));
        items.add(new Widgets.Item("用手机配置", IconView.PHONE));
        Widgets.showSheet(this, "先连接 DeepSeek", items, index -> {
            if (index == 0) openSettings("deepseek");
            else openSettings("phone");
        });
    }

    private void startVoiceFlow() {
        if (!settings.hasDeepSeekKey()) {
            promptDeepSeekKey();
            return;
        }
        if (!hasAsrCredentials()) {
            promptAsrSetup();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingVoiceMode = 1;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        startVoiceRecording(false, false);
    }

    private void beginHoldVoice() {
        if (streaming) {
            Ui.toast(this, "正在生成，稍后再按住说话");
            return;
        }
        if (!settings.hasDeepSeekKey()) {
            promptDeepSeekKey();
            return;
        }
        if (!hasAsrCredentials()) {
            promptAsrSetup();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingVoiceMode = 2;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        startVoiceRecording(true, true);
    }

    private void promptAsrSetup() {
        List<Widgets.Item> items = new ArrayList<>();
        items.add(new Widgets.Item("填写语音识别 Key", IconView.EDIT));
        items.add(new Widgets.Item("用手机配置", IconView.PHONE));
        Widgets.showSheet(this, "语音识别还未配置", items, index -> openSettings("asr"));
    }

    private void startVoiceRecording(boolean autoSend, boolean holdUi) {
        if (holdUi && holdVoice == null) return;
        if (recorder != null) {
            recorder.release();
            recorder = null;
        }
        voiceBusy = false;
        holdUiMode = holdUi;
        voiceAutoSend = autoSend;
        final int session = ++voiceSession;
        boolean whisper = settings.isWhisperMode();
        if (holdUi) {
            hideKeyboard();
            holdVoice.show(whisper, settings.isHoldFastVoice());
        } else {
            voice.show(whisper);
        }
        recorder = new AudioRecorder(this, new AudioRecorder.Listener() {
            @Override
            public void onLevel(float level, long elapsedMs) {
                App.MAIN.post(() -> {
                    if (session != voiceSession) return;
                    if (holdUiMode) {
                        if (holdVoice.isShowing()) {
                            holdVoice.setLevel(level);
                            holdVoice.setTimer(elapsedMs);
                        }
                    } else if (voice.isShowing()) {
                        voice.setLevel(level);
                        voice.setTimer(elapsedMs);
                    }
                });
            }

            @Override
            public void onFinished(byte[] wav, long durationMs, boolean hadSpeech) {
                App.MAIN.post(() -> {
                    if (session != voiceSession) return;
                    handleVoiceFinished(wav, durationMs, hadSpeech);
                });
            }

            @Override
            public void onError(String message) {
                App.MAIN.post(() -> {
                    if (session != voiceSession) return;
                    showVoiceError(message);
                });
            }
        });
        boolean ok = recorder.start(whisper);
        if (!ok) {
            showVoiceError(recorder.getLastError());
            return;
        }
        voiceBusy = true;
        Ui.haptic(this, 18);
    }

    private void showVoiceError(String message) {
        voiceBusy = false;
        if (holdUiMode && holdVoice != null && holdVoice.isShowing()) {
            holdVoice.showError(message);
        } else if (voice != null && voice.isShowing()) {
            voice.showError(message);
        } else {
            Ui.toast(this, message == null ? "没有听清" : message);
        }
    }

    private void showVoiceTranscribing() {
        if (holdUiMode && holdVoice != null && holdVoice.isShowing()) {
            holdVoice.showTranscribing();
        } else if (voice != null && voice.isShowing()) {
            voice.showTranscribing();
        }
    }

    private boolean isVoiceUiShowing() {
        return (holdVoice != null && holdVoice.isShowing())
                || (voice != null && voice.isShowing());
    }

    private void stopVoiceRecording() {
        if (recorder == null || !voiceBusy) return;
        boolean recording = holdUiMode
                ? (holdVoice != null && holdVoice.isShowing())
                : voice.isRecording();
        if (!recording) return;
        showVoiceTranscribing();
        recorder.stop();
    }

    private void handleVoiceFinished(byte[] wav, long durationMs, boolean hadSpeech) {
        if (!isVoiceUiShowing()) return;
        if (wav == null || wav.length < 1000) {
            showVoiceError("录音太短，再说一次吧");
            return;
        }
        showVoiceTranscribing();
        final int session = voiceSession;
        final boolean autoSend = voiceAutoSend;
        App.IO.execute(() -> {
            AsrClient asr = new AsrClient();
            asr.transcribe(settings, wav, new AsrClient.Callback() {
                @Override
                public void onResult(String text) {
                    boolean holdFast = autoSend && settings.isHoldFastVoice();
                    String prepared = com.wrist.deepseek.core.MathSpeech.normalize(
                            text, settings.getSubjectMode());
                    String fixed = holdFast
                            ? prepared
                            : fixer.fix(settings, store.messages(conversationId), prepared);
                    App.MAIN.post(() -> applyVoiceText(fixed, session, autoSend));
                }

                @Override
                public void onError(String message) {
                    App.MAIN.post(() -> {
                        if (session != voiceSession) return;
                        showVoiceError(message);
                    });
                }
            });
        });
    }

    private void applyVoiceText(String text, int session, boolean autoSend) {
        if (session != voiceSession) return;
        voiceBusy = false;
        if (!isVoiceUiShowing()) return;
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) {
            showVoiceError("没有听清，再说一次吧");
            return;
        }
        if (holdVoice != null && holdVoice.isShowing()) holdVoice.hide();
        if (voice != null && voice.isShowing()) voice.hide();
        holdUiMode = false;
        voiceAutoSend = false;
        input.setText(clean);
        input.setSelection(input.length());
        input.clearFocus();
        updateComposer();
        autoScroll = true;
        scrollToBottom();
        Ui.haptic(this, 12);
        if (autoSend) {
            settings.setDraft("");
            sendMessage(null);
        } else {
            settings.setDraft(clean);
            Ui.toast(this, "已转写，点发送");
        }
    }

    private void cancelHoldCandidate() {
        holdCandidate = false;
        holdTriggered = false;
        App.MAIN.removeCallbacks(holdStartRunnable);
    }

    private void cancelVoice() {
        voiceSession++;
        App.MAIN.removeCallbacks(holdStartRunnable);
        holdCandidate = false;
        holdTriggered = false;
        if (recorder != null) {
            recorder.cancel();
            recorder = null;
        }
        voiceBusy = false;
        holdUiMode = false;
        voiceAutoSend = false;
        holdCancelArmed = false;
        if (voice != null && voice.isShowing()) voice.hide();
        if (holdVoice != null && holdVoice.isShowing()) holdVoice.hide();
        hideKeyboard();
    }

    private void retryVoice() {
        voiceSession++;
        if (recorder != null) {
            recorder.release();
            recorder = null;
        }
        voiceBusy = false;
        holdUiMode = false;
        voiceAutoSend = false;
        startVoiceRecording(false, false);
    }

    private boolean hasAsrCredentials() {
        if (settings.hasAsrKey()) return true;
        String e = settings.getAsrEndpoint();
        return e.contains("127.0.0.1") || e.contains("192.168.") || e.contains("10.0.")
                || e.contains("://localhost");
    }

    private void showKeyboard() {
        input.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 100);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
            input.clearFocus();
        } catch (Throwable ignored) {
        }
    }

    private boolean isAtBottom() {
        if (scrollView == null || messageList == null) return true;
        View child = scrollView.getChildAt(0);
        if (child == null) return true;
        return scrollView.getScrollY() + scrollView.getHeight() >= child.getHeight() - Ui.dp(this, 6);
    }

    private void scrollToBottom() {
        if (scrollView == null) return;
        scrollView.post(() -> {
            if (scrollView == null) return;
            View child = scrollView.getChildAt(0);
            if (child == null) return;
            int y = Math.max(0, child.getHeight() - scrollView.getHeight());
            // Do not use fullScroll(): it can move focus to the ScrollView and
            // immediately dismiss the keyboard while the user taps the input.
            scrollView.scrollTo(0, y);
        });
    }

    private void updateScrollDown() {
        if (scrollDown == null) return;
        scrollDown.setVisibility(!autoScroll && !isAtBottom() ? View.VISIBLE : View.GONE);
    }

    private RippleDrawable circleRipple(int color, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(color), null,
                Ui.round(Color.WHITE, radiusDp, this));
    }

    private RippleDrawable circleRippleColor(int color, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), Ui.round(color, radiusDp, this),
                Ui.round(Color.WHITE, radiusDp, this));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO) {
            int mode = pendingVoiceMode;
            pendingVoiceMode = 0;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mode == 2) {
                    Ui.toast(this, "麦克风已允许，请再按住输入框说话");
                } else {
                    startVoiceRecording(false, false);
                }
            } else {
                Widgets.showInfo(this, "需要麦克风权限", "语音输入需要录音权限。请在系统设置中允许本应用使用麦克风。", "知道了");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_HISTORY && resultCode == RESULT_OK && data != null) {
            long id = data.getLongExtra("conversation_id", -1);
            if (id == 0) newChat();
            else if (id > 0) loadConversation(id, true);
        } else if (requestCode == REQ_SETTINGS) {
            refreshWelcome();
            refreshTop();
            updateComposer();
            applyClockVisibility();
            showHoldHintOnce();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyClockVisibility();
        if (privateMode && !settings.hasPrivacyPin()) {
            privateMode = false;
            startEmptySpace();
            refreshTop();
        }
        if (!streaming && conversationId > 0
                && (!store.conversationExists(conversationId)
                || store.isPrivateConversation(conversationId) != privateMode)) {
            startEmptySpace();
        }
        if (getWindow() != null) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
        refreshWelcome();
        refreshTop();
    }

    @Override
    protected void onPause() {
        if (input != null) settings.setDraft(input.getText().toString());
        if (isVoiceUiShowing()) {
            cancelVoice();
        }
        if (tts != null) tts.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        App.MAIN.removeCallbacks(dotsTicker);
        App.MAIN.removeCallbacks(streamRender);
        App.MAIN.removeCallbacks(holdStartRunnable);
        App.MAIN.removeCallbacks(clockTicker);
        if (activeClient != null) activeClient.cancel();
        if (recorder != null) recorder.release();
        if (tts != null) {
            try {
                tts.shutdown();
            } catch (Throwable ignored) {
            }
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (isVoiceUiShowing()) {
            cancelVoice();
            return;
        }
        super.onBackPressed();
    }

    private class MessageViewHolder {
        final LinearLayout wrapper;
        final LinearLayout reasonBox;
        final TextView reasonHeader;
        final TextView reasonBody;
        final IconView chevron;
        final TextView body;
        TextView statusLine;
        TextView sourcesLine;
        Message message;
        boolean expanded;
        long thinkStarted;
        long thinkEnded;

        MessageViewHolder(Message m) {
            message = m;
            wrapper = new LinearLayout(MainActivity.this);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.setPadding(0, Ui.dp(MainActivity.this, 5), 0, Ui.dp(MainActivity.this, 5));
            if (m.isUser()) {
                wrapper.setGravity(Gravity.END);
            } else {
                wrapper.setGravity(Gravity.START);
            }
            wrapper.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            reasonBox = new LinearLayout(MainActivity.this);
            reasonBox.setOrientation(LinearLayout.VERTICAL);
            reasonBox.setBackground(Ui.roundStroke(0xFF14141A, 14, 0xFF25252F, 1, MainActivity.this));
            reasonBox.setPadding(Ui.dp(MainActivity.this, 10), Ui.dp(MainActivity.this, 7),
                    Ui.dp(MainActivity.this, 10), Ui.dp(MainActivity.this, 8));
            reasonBox.setVisibility(View.GONE);

            LinearLayout head = new LinearLayout(MainActivity.this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setClickable(true);
            head.setMinimumHeight(Ui.dp(MainActivity.this, 30));
            IconView spark = new IconView(MainActivity.this, IconView.SPARK, Ui.ACCENT, 14);
            spark.setClickable(false);
            head.addView(spark);
            reasonHeader = Ui.mediumText(MainActivity.this, "深度思考", 12, Ui.MUTED);
            LinearLayout.LayoutParams rhlp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            rhlp.leftMargin = Ui.dp(MainActivity.this, 6);
            head.addView(reasonHeader, rhlp);
            chevron = new IconView(MainActivity.this, IconView.CHEVRON_RIGHT, Ui.DIM, 17);
            chevron.setClickable(false);
            head.addView(chevron);
            head.setOnClickListener(v -> {
                expanded = !expanded;
                bind(message, message != null && message.status == 1);
            });
            reasonBox.addView(head);

            reasonBody = Ui.text(MainActivity.this, "", 12.5f, 0xFFB9BBC8);
            reasonBody.setLineSpacing(Ui.dp(MainActivity.this, 3), 1);
            reasonBody.setPadding(0, Ui.dp(MainActivity.this, 7), 0, 0);
            reasonBody.setVisibility(View.GONE);
            reasonBox.addView(reasonBody);

            body = new TextView(MainActivity.this);
            body.setTextSize(15);
            body.setTextColor(Ui.TEXT);
            body.setTypeface(Ui.regular());
            body.setIncludeFontPadding(false);
            body.setLineSpacing(Ui.dp(MainActivity.this, 4.5f), 1);
            body.setTextIsSelectable(false);
            body.setLongClickable(true);
            body.setOnLongClickListener(v -> {
                if (message != null) showMessageActions(message);
                return true;
            });
            if (m.isUser()) {
                body.setMaxWidth(Ui.dp(MainActivity.this, 252));
                body.setBackground(Ui.bubble(Ui.USER_BUBBLE, MainActivity.this, true));
                body.setPadding(Ui.dp(MainActivity.this, 11), Ui.dp(MainActivity.this, 8),
                        Ui.dp(MainActivity.this, 11), Ui.dp(MainActivity.this, 8));
                body.setTextColor(Color.WHITE);
                wrapper.addView(body, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                statusLine = null;
                sourcesLine = null;
            } else {
                wrapper.addView(reasonBox);
                wrapper.addView(body, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                statusLine = Ui.text(MainActivity.this, "", 10.5f, Ui.DIM);
                statusLine.setPadding(0, Ui.dp(MainActivity.this, 5), 0, 0);
                statusLine.setVisibility(View.GONE);
                wrapper.addView(statusLine);

                sourcesLine = Ui.mediumText(MainActivity.this, "", 10.5f, Ui.BLUE_LIGHT);
                sourcesLine.setPadding(0, Ui.dp(MainActivity.this, 5), 0, 0);
                sourcesLine.setVisibility(View.GONE);
                sourcesLine.setClickable(true);
                wrapper.addView(sourcesLine);
            }
            bind(m, m.status == 1);
        }

        void bind(Message m, boolean isStreaming) {
            if (m == null) return;
            this.message = m;
            if (m.isUser()) {
                body.setText(m.content == null ? "" : m.content);
                return;
            }
            if (m.searching) {
                reasonBox.setVisibility(View.GONE);
                body.setTextColor(Ui.MUTED);
                body.setText("正在搜索网页…");
                statusLine.setVisibility(View.VISIBLE);
                statusLine.setTextColor(Ui.BLUE_LIGHT);
                statusLine.setText("正在联网搜索…");
                statusLine.setOnClickListener(null);
                sourcesLine.setVisibility(View.GONE);
                return;
            }
            boolean hasReasoning = !TextUtils.isEmpty(m.reasoning);
            reasonBox.setVisibility(hasReasoning ? View.VISIBLE : View.GONE);
            if (hasReasoning) {
                if (m.status == 1) {
                    if (thinkStarted == 0) thinkStarted = System.currentTimeMillis();
                    reasonHeader.setText("深度思考中…");
                } else {
                    if (thinkEnded == 0) thinkEnded = System.currentTimeMillis();
                    long sec = thinkStarted > 0 ? Math.max(1, (thinkEnded - thinkStarted) / 1000) : 0;
                    reasonHeader.setText(sec > 0 ? "已深度思考 · " + sec + " 秒" : "已深度思考");
                }
                if (expanded) {
                    reasonBody.setText(Markdown.render(m.reasoning, MainActivity.this));
                    reasonBody.setVisibility(View.VISIBLE);
                } else {
                    reasonBody.setVisibility(View.GONE);
                }
                chevron.setIcon(expanded ? IconView.CHEVRON_DOWN : IconView.CHEVRON_RIGHT);
            }

            if (TextUtils.isEmpty(m.content)) {
                body.setTextColor(m.status == 2 ? Ui.ERROR : Ui.MUTED);
                if (m.status == 2) {
                    body.setText("生成失败");
                } else if (m.status == 3) {
                    body.setText("已停止");
                } else {
                    body.setText("正在思考" + dots());
                }
            } else {
                body.setTextColor(Ui.TEXT);
                CharSequence rendered = Markdown.render(m.content, MainActivity.this);
                if (isStreaming) {
                    SpannableStringBuilder b = new SpannableStringBuilder(rendered);
                    int start = b.length();
                    b.append(" ▍");
                    b.setSpan(new ForegroundColorSpan(Ui.ACCENT), start, b.length(),
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    body.setText(b);
                } else {
                    body.setText(rendered);
                }
            }

            if (m.status == 2) {
                statusLine.setVisibility(View.VISIBLE);
                statusLine.setTextColor(Ui.ERROR);
                statusLine.setText("生成中断 · 点此重试");
                statusLine.setOnClickListener(v -> {
                    if (message != null) retryMessage(message);
                });
            } else if (m.status == 3) {
                statusLine.setVisibility(View.VISIBLE);
                statusLine.setTextColor(Ui.DIM);
                statusLine.setText("已停止生成");
                statusLine.setOnClickListener(null);
            } else {
                statusLine.setVisibility(View.GONE);
            }

            java.util.List<SearchResult> sources = SearchResult.listFromJson(m.sources);
            if (!sources.isEmpty()) {
                sourcesLine.setVisibility(View.VISIBLE);
                sourcesLine.setTextColor(Ui.BLUE_LIGHT);
                sourcesLine.setText("来源 · " + sources.size() + " 个网页");
                sourcesLine.setOnClickListener(v -> showSources(sources));
            } else if (!TextUtils.isEmpty(m.searchNote)) {
                sourcesLine.setVisibility(View.VISIBLE);
                sourcesLine.setTextColor(Ui.DIM);
                sourcesLine.setText(m.searchNote);
                sourcesLine.setOnClickListener(null);
            } else {
                sourcesLine.setVisibility(View.GONE);
            }
        }

        private String dots() {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < (dotPhase % 4); i++) b.append('·');
            return b.toString();
        }
    }
}
