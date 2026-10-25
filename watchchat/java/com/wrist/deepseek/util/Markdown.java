package com.wrist.deepseek.util;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;

/**
 * A deliberately small Markdown renderer for chat replies: headings, bold,
 * italic, inline code, fenced code, lists, quotes and links. It never throws
 * on half-streamed markup (unclosed fences are treated as code until closed).
 */
public final class Markdown {
    private Markdown() {
    }

    public static CharSequence render(String md, Context c) {
        if (md == null || md.isEmpty()) return "";
        try {
            SpannableStringBuilder out = new SpannableStringBuilder();
            String[] lines = md.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
            boolean code = false;
            boolean first = true;
            for (String raw : lines) {
                String line = raw;
                if (!first) out.append('\n');
                first = false;

                if (line.trim().startsWith("```")) {
                    if (code) {
                        code = false;
                    } else {
                        code = true;
                    }
                    continue;
                }
                if (code) {
                    appendCodeLine(out, line, c);
                    continue;
                }
                if (line.trim().isEmpty()) {
                    continue;
                }
                if (line.matches("^\\s*\\|?\\s*:?-{2,}.*")) {
                    continue;
                }
                if (line.matches("^\\s*([-*_]\\s*){3,}$")) {
                    int start = out.length();
                    out.append("────────────");
                    out.setSpan(new ForegroundColorSpan(0xFF34343E), start, out.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    continue;
                }
                if (line.startsWith("#")) {
                    int i = 0;
                    while (i < line.length() && i < 6 && line.charAt(i) == '#') i++;
                    String t = line.substring(i).trim();
                    int start = out.length();
                    inline(out, t, c);
                    out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    float scale = i <= 2 ? 1.24f : 1.12f;
                    out.setSpan(new RelativeSizeSpan(scale), start, out.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.append('\n');
                    continue;
                }
                if (line.startsWith(">")) {
                    String t = line.substring(1);
                    if (t.startsWith(" ")) t = t.substring(1);
                    int start = out.length();
                    out.append("▎ ");
                    out.setSpan(new ForegroundColorSpan(Ui.ACCENT), start, out.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    int content = out.length();
                    inline(out, t, c);
                    out.setSpan(new ForegroundColorSpan(Ui.MUTED), content, out.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    continue;
                }
                if (line.matches("^\\s*[-*+]\\s+.*")) {
                    int idx = 0;
                    while (idx < line.length() && Character.isWhitespace(line.charAt(idx))) idx++;
                    String t = line.substring(idx + 1).trim();
                    int start = out.length();
                    out.append("•  ");
                    out.setSpan(new ForegroundColorSpan(Ui.ACCENT), start, out.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    inline(out, t, c);
                    continue;
                }
                if (line.matches("^\\s*\\d+[.)]\\s+.*")) {
                    inline(out, line.trim(), c);
                    continue;
                }
                if (line.indexOf('|') >= 0 && line.indexOf('|') != line.lastIndexOf('|')) {
                    // Tables are rare on a 300 dp watch; render as aligned text.
                    String t = line.trim();
                    if (t.startsWith("|")) t = t.substring(1);
                    if (t.endsWith("|")) t = t.substring(0, t.length() - 1);
                    String[] cells = t.split("\\|");
                    StringBuilder row = new StringBuilder();
                    for (int i = 0; i < cells.length; i++) {
                        if (i > 0) row.append("  ·  ");
                        row.append(cells[i].trim());
                    }
                    inline(out, row.toString(), c);
                    continue;
                }
                inline(out, line, c);
            }
            if (out.length() > 30000) {
                out.delete(30000, out.length());
                out.append("\n…");
            }
            return out;
        } catch (Throwable t) {
            return md;
        }
    }

    private static void appendCodeLine(SpannableStringBuilder out, String line, Context c) {
        int start = out.length();
        out.append("  ").append(line);
        out.setSpan(new TypefaceSpan("monospace"), start, out.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new AbsoluteSizeSpan(Ui.sp(c, 12.5f)), start, out.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new ForegroundColorSpan(0xFFD4D7E3), start, out.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new BackgroundColorSpan(0xFF17171E), start, out.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void inline(SpannableStringBuilder out, String s, Context c) {
        int i = 0;
        int bold = -1, italic = -1, strike = -1, code = -1;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                out.append(s.charAt(i + 1));
                i += 2;
                continue;
            }
            if (code < 0 && i + 1 < s.length() && s.charAt(i) == '*' && s.charAt(i + 1) == '*') {
                if (bold >= 0) {
                    out.setSpan(new StyleSpan(Typeface.BOLD), bold, out.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    bold = -1;
                } else {
                    bold = out.length();
                }
                i += 2;
                continue;
            }
            if (code < 0 && ch == '*') {
                if (italic >= 0) {
                    out.setSpan(new StyleSpan(Typeface.ITALIC), italic, out.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    italic = -1;
                } else {
                    italic = out.length();
                }
                i++;
                continue;
            }
            if (code < 0 && i + 1 < s.length() && s.charAt(i) == '~' && s.charAt(i + 1) == '~') {
                if (strike >= 0) {
                    out.setSpan(new StrikethroughSpan(), strike, out.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    strike = -1;
                } else {
                    strike = out.length();
                }
                i += 2;
                continue;
            }
            if (ch == '`') {
                if (code >= 0) {
                    out.setSpan(new TypefaceSpan("monospace"), code, out.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new BackgroundColorSpan(0xFF24242D), code, out.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new ForegroundColorSpan(0xFFE2E4F0), code, out.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new AbsoluteSizeSpan(Ui.sp(c, 13.5f)), code, out.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    code = -1;
                } else {
                    code = out.length();
                }
                i++;
                continue;
            }
            if (code < 0 && ch == '[') {
                int close = s.indexOf("](", i + 1);
                int end = close >= 0 ? s.indexOf(')', close + 2) : -1;
                if (close > 0 && end > 0) {
                    String label = s.substring(i + 1, close);
                    int start = out.length();
                    out.append(label);
                    out.setSpan(new ForegroundColorSpan(Ui.BLUE_LIGHT), start, out.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new UnderlineSpan(), start, out.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 1;
                    continue;
                }
            }
            if (code >= 0) {
                out.append(ch);
                i++;
                continue;
            }
            out.append(ch);
            i++;
        }
        int end = out.length();
        if (bold >= 0) out.setSpan(new StyleSpan(Typeface.BOLD), bold, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (italic >= 0) out.setSpan(new StyleSpan(Typeface.ITALIC), italic, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (strike >= 0) out.setSpan(new StrikethroughSpan(), strike, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (code >= 0) {
            out.setSpan(new TypefaceSpan("monospace"), code, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new BackgroundColorSpan(0xFF24242D), code, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}
