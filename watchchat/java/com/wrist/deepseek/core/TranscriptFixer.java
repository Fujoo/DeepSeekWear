package com.wrist.deepseek.core;

import android.text.TextUtils;

import java.util.List;
import java.util.Locale;

/** Uses DeepSeek to turn a rough ASR transcript into the sentence the user meant. */
public class TranscriptFixer {
    public String fix(Settings settings, List<Message> recent, String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        if (!settings.isAutoCorrect() || !settings.hasDeepSeekKey()) return raw.trim();
        if (raw.length() > 500) return raw.trim();
        try {
            DeepSeekClient client = new DeepSeekClient();
            String prompt = Prompts.correctionUser(recent, raw);
            int max = Math.max(96, Math.min(480, raw.length() * 3));
            String out = client.complete(settings, Prompts.correctionSystem(), prompt, max);
            String cleaned = clean(out, raw);
            return TextUtils.isEmpty(cleaned) ? raw.trim() : cleaned;
        } catch (Throwable t) {
            return raw.trim();
        }
    }

    private static String clean(String out, String raw) {
        if (out == null) return "";
        String s = out.trim();
        s = s.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("\\s*```$", "");
        s = s.replaceAll("^(纠正后|修正后|校正后|转写结果|文本|结果)\\s*[：:]\\s*", "");
        s = s.replaceAll("^[\"'“”‘’`]+", "").replaceAll("[\"'“”‘’`]+$", "");
        // Drop an explanation line if a model ignored "only output the text".
        String[] lines = s.split("\\r?\\n");
        if (lines.length > 1) {
            StringBuilder keep = new StringBuilder();
            for (String line : lines) {
                String t = line.trim();
                if (t.startsWith("解释") || t.startsWith("说明") || t.startsWith("（")
                        || t.startsWith("(") || t.startsWith("注意")) break;
                if (t.isEmpty()) continue;
                if (keep.length() > 0) keep.append(' ');
                keep.append(t);
            }
            s = keep.toString();
        }
        s = s.replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) return "";
        if (s.startsWith("抱歉") || s.startsWith("无法") || s.startsWith("作为")) return "";
        int maxLen = Math.max(60, raw.trim().length() * 2 + 30);
        if (s.length() > maxLen) return "";
        String r = raw.replaceAll("[\\p{P}\\p{S}\\s]", "");
        String c = s.replaceAll("[\\p{P}\\p{S}\\s]", "");
        if (r.length() > 8 && c.length() > r.length() * 2.2) return "";
        return s;
    }

    public static String label() {
        return String.format(Locale.CHINA, "%s", "上下文纠错");
    }
}
