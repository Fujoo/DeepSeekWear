package com.wrist.deepseek.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** All user preferences live here. API keys are encrypted by {@link SecureStore}. */
public class Settings {
    public static final String DEFAULT_DS_BASE = "https://api.deepseek.com";
    public static final String DEFAULT_DS_MODEL = "deepseek-flash";
    public static final String DEFAULT_ASR_ENDPOINT = "https://api.siliconflow.cn/v1/audio/transcriptions";
    public static final String DEFAULT_ASR_MODEL = "TeleAI/TeleSpeechASR";
    private static final String OLD_DEFAULT_ASR_MODEL = "FunAudioLLM/SenseVoiceSmall";

    private final SharedPreferences p;
    private final SecureStore secrets;

    public Settings(Context context) {
        p = context.getSharedPreferences("watchdeepseek", Context.MODE_PRIVATE);
        secrets = new SecureStore();
        if (!p.getBoolean("asr_default_telespeech", false)) {
            String current = p.getString("asr_model", "");
            SharedPreferences.Editor e = p.edit().putBoolean("asr_default_telespeech", true);
            if (TextUtils.isEmpty(current) || OLD_DEFAULT_ASR_MODEL.equals(current.trim())) {
                e.putString("asr_model", DEFAULT_ASR_MODEL);
            }
            e.apply();
        }
    }

    private String s(String key, String def) {
        String v = p.getString(key, def);
        return v == null ? def : v;
    }

    public String getDeepSeekKey() {
        return secrets.decrypt(s("ds_key", ""));
    }

    public void setDeepSeekKey(String value) {
        p.edit().putString("ds_key", secrets.encrypt(value == null ? "" : value.trim())).apply();
    }

    public String getAsrKey() {
        return secrets.decrypt(s("asr_key", ""));
    }

    public void setAsrKey(String value) {
        p.edit().putString("asr_key", secrets.encrypt(value == null ? "" : value.trim())).apply();
    }

    public String getDeepSeekBase() {
        String v = s("ds_base", DEFAULT_DS_BASE).trim();
        if (v.isEmpty()) v = DEFAULT_DS_BASE;
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }

    public void setDeepSeekBase(String value) {
        p.edit().putString("ds_base", value == null ? "" : value.trim()).apply();
    }

    /** Accepts either an API root or a full /chat/completions URL. */
    public String getChatEndpoint() {
        String base = getDeepSeekBase();
        if (base.endsWith("/chat/completions")) return base;
        return base + "/chat/completions";
    }

    public String getModelsEndpoint() {
        String base = getDeepSeekBase();
        if (base.endsWith("/chat/completions")) {
            return base.substring(0, base.length() - "/chat/completions".length()) + "/models";
        }
        return base + "/models";
    }

    public String getSearchEndpoint() {
        String base = getDeepSeekBase();
        if (base.endsWith("/v1/tools/search")) return base;
        if (base.endsWith("/chat/completions")) {
            base = base.substring(0, base.length() - "/chat/completions".length());
        }
        if (base.endsWith("/v1")) return base + "/tools/search";
        return base + "/v1/tools/search";
    }

    public boolean isSearchEnabled() {
        return p.getBoolean("web_search", false);
    }

    public void setSearchEnabled(boolean value) {
        p.edit().putBoolean("web_search", value).apply();
    }

    public String getModel() {
        String v = s("ds_model", DEFAULT_DS_MODEL).trim();
        // Migrate names used by older builds of this app to the current V4 API.
        if (v.isEmpty() || "deepseek-chat".equals(v) || "deepseek-reasoner".equals(v)
                || "deepseek-v4-flash".equals(v)) {
            return DEFAULT_DS_MODEL;
        }
        return v;
    }

    public void setModel(String value) {
        p.edit().putString("ds_model", value).apply();
    }

    public boolean isThinking() {
        return p.getBoolean("thinking", false);
    }

    public void setThinking(boolean value) {
        p.edit().putBoolean("thinking", value).apply();
    }

    public String getAsrEndpoint() {
        String v = s("asr_endpoint", DEFAULT_ASR_ENDPOINT).trim();
        if (v.isEmpty()) v = DEFAULT_ASR_ENDPOINT;
        return v;
    }

    public void setAsrEndpoint(String value) {
        p.edit().putString("asr_endpoint", value == null ? "" : value.trim()).apply();
    }

    public String getAsrModel() {
        String v = s("asr_model", DEFAULT_ASR_MODEL).trim();
        return v.isEmpty() ? DEFAULT_ASR_MODEL : v;
    }

    public void setAsrModel(String value) {
        p.edit().putString("asr_model", value == null ? "" : value.trim()).apply();
    }

    public String getAsrPreset() {
        return s("asr_preset", "siliconflow");
    }

    public void applyAsrPreset(String preset) {
        if ("siliconflow".equals(preset)) {
            setAsrEndpoint(DEFAULT_ASR_ENDPOINT);
            setAsrModel(DEFAULT_ASR_MODEL);
        } else if ("openai".equals(preset)) {
            setAsrEndpoint("https://api.openai.com/v1/audio/transcriptions");
            setAsrModel("whisper-1");
        } else if ("groq".equals(preset)) {
            setAsrEndpoint("https://api.groq.com/openai/v1/audio/transcriptions");
            setAsrModel("whisper-large-v3-turbo");
        }
        p.edit().putString("asr_preset", preset).apply();
    }

    public boolean isWhisperMode() {
        return p.getBoolean("whisper_mode", true);
    }

    public void setWhisperMode(boolean value) {
        p.edit().putBoolean("whisper_mode", value).apply();
    }

    public boolean isAutoCorrect() {
        return p.getBoolean("auto_correct", true);
    }

    public void setAutoCorrect(boolean value) {
        p.edit().putBoolean("auto_correct", value).apply();
    }

    public boolean isTtsEnabled() {
        return p.getBoolean("tts", false);
    }

    public void setTtsEnabled(boolean value) {
        p.edit().putBoolean("tts", value).apply();
    }

    public boolean isHapticsEnabled() {
        return p.getBoolean("haptics", true);
    }

    public void setHapticsEnabled(boolean value) {
        p.edit().putBoolean("haptics", value).apply();
    }

    public boolean isCompactPrompt() {
        return p.getBoolean("compact_prompt", true);
    }

    public void setCompactPrompt(boolean value) {
        p.edit().putBoolean("compact_prompt", value).apply();
    }

    public String getCustomPrompt() {
        return s("custom_prompt", "");
    }

    public void setCustomPrompt(String value) {
        p.edit().putString("custom_prompt", value == null ? "" : value.trim()).apply();
    }

    /** Hold-to-talk uses a faster path by default: skip the extra correction round-trip. */
    public boolean isHoldFastVoice() {
        return p.getBoolean("hold_fast", true);
    }

    public void setHoldFastVoice(boolean value) {
        p.edit().putBoolean("hold_fast", value).apply();
    }

    /** true: drawer slides in from the left. false: the original bottom sheet. */
    public boolean isSideMenu() {
        return p.getBoolean("side_menu", true);
    }

    public void setSideMenu(boolean value) {
        p.edit().putBoolean("side_menu", value).apply();
    }

    public boolean isShowClock() {
        return p.getBoolean("show_clock", true);
    }

    public void setShowClock(boolean value) {
        p.edit().putBoolean("show_clock", value).apply();
    }

    public static final float MIN_UI_SCALE = 0.50f;
    public static final float MAX_UI_SCALE = 1.30f;

    public float getUiScale() {
        float v = p.getFloat("ui_scale", 1f);
        return Math.max(MIN_UI_SCALE, Math.min(MAX_UI_SCALE, v));
    }

    public void setUiScale(float value) {
        p.edit().putFloat("ui_scale", Math.max(MIN_UI_SCALE,
                Math.min(MAX_UI_SCALE, value))).apply();
    }

    public boolean isScaleConfigured() {
        return p.getBoolean("ui_scale_configured", false)
                || p.getBoolean("ui_scale_configured_v2", false)
                || p.getBoolean("ui_scale_configured_v3", false);
    }

    public void setScaleConfigured(boolean value) {
        p.edit().putBoolean("ui_scale_configured_v3", value).apply();
    }

    public boolean isTutorialShown() {
        return p.getBoolean("tutorial_v150", false)
                || p.getBoolean("tutorial_v150_release", false)
                || p.getBoolean("tutorial_v150_final", false)
                || p.getBoolean("tutorial_v150_delivery2", false);
    }

    public void setTutorialShown(boolean value) {
        p.edit().putBoolean("tutorial_v150_delivery2", value).apply();
    }

    public boolean isHoldHintShown() {
        return p.getBoolean("hold_hint_v130", false);
    }

    public void setHoldHintShown(boolean value) {
        p.edit().putBoolean("hold_hint_v130", value).apply();
    }

    public long getLastConversation() {
        return p.getLong("last_conversation", 0);
    }

    public long getPrivacyLastConversation() {
        return p.getLong("privacy_last_conversation", 0);
    }

    public void setPrivacyLastConversation(long id) {
        p.edit().putLong("privacy_last_conversation", id).apply();
    }

    /** PINs are never stored in plain text: PBKDF2-HMAC-SHA1, 12k rounds, per-device salt. */
    public boolean hasPrivacyPin() {
        return !s("privacy_pin", "").isEmpty();
    }

    public void setPrivacyPin(String pin) {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            byte[] hash = pbkdf2(pin, salt, 12000);
            String stored = "v1:12000:" + Base64.encodeToString(salt, Base64.NO_WRAP)
                    + ":" + Base64.encodeToString(hash, Base64.NO_WRAP);
            p.edit().putString("privacy_pin", stored).apply();
        } catch (Throwable ignored) {
        }
    }

    public boolean verifyPrivacyPin(String pin) {
        if (pin == null || !hasPrivacyPin()) return false;
        try {
            String[] parts = s("privacy_pin", "").split(":");
            if (parts.length != 4) return false;
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.decode(parts[2], Base64.NO_WRAP);
            byte[] expected = Base64.decode(parts[3], Base64.NO_WRAP);
            byte[] actual = pbkdf2(pin, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (Throwable t) {
            return false;
        }
    }

    public void clearPrivacyPin() {
        p.edit().remove("privacy_pin").remove("privacy_last_conversation").apply();
    }

    private static byte[] pbkdf2(String pin, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, iterations, 256);
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
    }

    public void setLastConversation(long id) {
        p.edit().putLong("last_conversation", id).apply();
    }

    public String getDraft() {
        return s("draft", "");
    }

    public void setDraft(String value) {
        p.edit().putString("draft", value == null ? "" : value).apply();
    }

    public boolean hasDeepSeekKey() {
        return !TextUtils.isEmpty(getDeepSeekKey());
    }

    public boolean hasAsrKey() {
        return !TextUtils.isEmpty(getAsrKey());
    }

    public String keyHint(String key) {
        if (TextUtils.isEmpty(key)) return "未设置";
        if (key.length() <= 8) return "已设置";
        return "已设置 · " + key.substring(key.length() - 4);
    }
}
