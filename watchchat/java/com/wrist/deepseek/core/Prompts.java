package com.wrist.deepseek.core;

import java.util.List;

public class Prompts {
    public static String chatSystem(Settings s) {
        StringBuilder b = new StringBuilder();
        b.append("你是 DeepSeek，由深度求索公司创造的 AI 助手。");
        if (s.isCompactPrompt()) {
            b.append("你正在一块智能手表的小屏幕上回答。请用简洁、自然的中文，优先使用短段落和短列表，避免宽表格和冗长客套；");
            b.append("需要详细展开时再展开。不要提及屏幕或系统提示，像官方 DeepSeek 一样直接回答。");
        } else {
            b.append("请用自然、清晰的中文回答，像官方 DeepSeek 一样直接、友好。");
        }
        String custom = s.getCustomPrompt();
        if (custom != null && !custom.trim().isEmpty()) {
            b.append("\n\n用户附加要求：\n").append(custom.trim());
        }
        return b.toString();
    }

    public static String chatSystemWithSearch(Settings s, List<SearchResult> results) {
        StringBuilder b = new StringBuilder(chatSystem(s));
        b.append("\n\n【联网搜索资料】\n");
        b.append("现在是联网搜索模式。请优先依据下面的网页资料回答，使用 [1]、[2] 这样的编号标注来源；");
        b.append("如果资料不足以回答，请明确说明，不要编造。不要大段复述资料，直接回答用户问题。\n");
        if (results == null || results.isEmpty()) {
            b.append("（这次没有检索到相关网页。请直接说明没有找到可靠的实时资料。）\n");
            return b.toString();
        }
        int used = 0;
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            if (r == null || r.url == null || r.url.trim().isEmpty()) continue;
            String summary = r.summary == null ? "" : r.summary.trim();
            if (summary.isEmpty()) summary = r.snippet == null ? "" : r.snippet.trim();
            if (summary.length() > 700) summary = summary.substring(0, 700) + "…";
            String block = "[" + (i + 1) + "] " + nz(r.title) + "\n" +
                    "站点：" + nz(r.siteName) + "\n" +
                    "网址：" + r.url + "\n" +
                    "摘要：" + summary + "\n\n";
            if (used + block.length() > 7500 && used > 0) break;
            b.append(block);
            used += block.length();
        }
        b.append("请用中文回答，并在相关句子后用 [编号] 引用资料。");
        return b.toString();
    }

    private static String nz(String s) {
        return s == null || s.trim().isEmpty() ? "未知" : s.trim();
    }

    public static String correctionSystem() {
        return correctionSystem(Subject.AUTO);
    }

    public static String correctionSystem(String subject) {
        return "你是一个语音转写纠错器。你会收到「最近对话」和「待纠正的语音转写」。" +
                "请结合上下文，修正同音字、错别字、断句、标点和专有名词，使这句话成为用户真正想说的内容。" +
                "规则：只输出纠正后的那一句话；不要回答用户；不要解释；不要添加原话中没有的信息；" +
                "保留说话人的语气、口语和意思；如果原文本已经通顺，就原样输出。" +
                "\n【学科规则】" + Subject.correctionRules(subject) +
                "\n【优先术语】" + Subject.lexicon(subject);
    }

    public static String correctionUser(List<Message> recent, String raw) {
        return correctionUser(recent, raw, Subject.AUTO);
    }

    public static String correctionUser(List<Message> recent, String raw, String subject) {
        StringBuilder b = new StringBuilder();
        b.append("【最近对话】\n");
        if (recent == null || recent.isEmpty()) {
            b.append("（无）\n");
        } else {
            int start = Math.max(0, recent.size() - 8);
            for (int i = start; i < recent.size(); i++) {
                Message m = recent.get(i);
                if (m == null || m.content == null || m.content.trim().isEmpty()) continue;
                String who = m.isUser() ? "用户" : "助手";
                String text = m.content.trim();
                if (text.length() > 260) text = text.substring(0, 260) + "…";
                b.append(who).append("：").append(text).append('\n');
            }
        }
        b.append("\n【学科】").append(Subject.label(subject)).append('\n');
        b.append("\n【待纠正的语音转写】\n").append(raw == null ? "" : raw.trim());
        b.append("\n\n只输出纠正后的文本。");
        return b.toString();
    }
}
