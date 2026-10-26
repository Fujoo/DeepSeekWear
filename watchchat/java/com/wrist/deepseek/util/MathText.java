package com.wrist.deepseek.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts common LaTeX math into readable Unicode for the watch's TextViews.
 * It deliberately stays lightweight: no WebView, no bundled TeX engine.
 */
public final class MathText {
    /** Private-use marker tells Markdown to center and tint a display formula. */
    public static final char BLOCK = '\uE000';
    public static final char FRAC_START = '\uE001';
    public static final char FRAC_SEP = '\uE002';
    public static final char FRAC_END = '\uE003';

    private static final Map<String, String> SYMBOLS = new HashMap<>();

    static {
        String[][] pairs = {
                {"cap", "∩"}, {"cup", "∪"}, {"mid", "|"}, {"cdot", "·"},
                {"times", "×"}, {"div", "÷"}, {"pm", "±"}, {"mp", "∓"},
                {"le", "≤"}, {"leq", "≤"}, {"ge", "≥"}, {"geq", "≥"},
                {"ne", "≠"}, {"neq", "≠"}, {"approx", "≈"}, {"equiv", "≡"},
                {"infty", "∞"}, {"pi", "π"}, {"theta", "θ"}, {"alpha", "α"},
                {"beta", "β"}, {"gamma", "γ"}, {"delta", "δ"}, {"lambda", "λ"},
                {"mu", "μ"}, {"sigma", "σ"}, {"phi", "φ"}, {"omega", "ω"},
                {"sum", "Σ"}, {"prod", "∏"}, {"int", "∫"}, {"partial", "∂"},
                {"nabla", "∇"}, {"forall", "∀"}, {"exists", "∃"}, {"in", "∈"},
                {"notin", "∉"}, {"subset", "⊂"}, {"subseteq", "⊆"},
                {"supset", "⊃"}, {"to", "→"}, {"rightarrow", "→"},
                {"leftarrow", "←"}, {"leftrightarrow", "↔"}, {"therefore", "∴"},
                {"because", "∵"}, {"ldots", "…"}, {"cdots", "⋯"}, {"dots", "…"},
                {"%", "%"}, {"$", "$"}, {"&", "&"}, {"#", "#"}, {"_", "_"},
                {"{", "{"}, {"}", "}"}, {" ", " "}, {",", " "}, {";", " "},
                {"!", ""}, {":", " "}
        };
        for (String[] p : pairs) SYMBOLS.put(p[0], p[1]);
    }

    private MathText() {
    }

    /** Format LaTeX math outside fenced code blocks. */
    public static String formatMarkdown(String md) {
        if (md == null || md.isEmpty()) return md == null ? "" : md;
        String[] lines = md.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder out = new StringBuilder(md.length() + 32);
        StringBuilder plain = new StringBuilder();
        boolean code = false;
        for (String line : lines) {
            if (line.trim().startsWith("```")) {
                if (!code) {
                    out.append(formatSegment(plain.toString()));
                    plain.setLength(0);
                }
                out.append(line).append('\n');
                code = !code;
                continue;
            }
            if (code) out.append(line).append('\n');
            else plain.append(line).append('\n');
        }
        if (!code) out.append(formatSegment(plain.toString()));
        return out.toString();
    }

    private static String formatSegment(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            int open = s.indexOf("\\[", i);
            int inline = s.indexOf("\\(", i);
            int next;
            boolean block;
            if (open < 0 && inline < 0) {
                out.append(s.substring(i));
                break;
            }
            if (open >= 0 && (inline < 0 || open < inline)) {
                next = open;
                block = true;
            } else {
                next = inline;
                block = false;
            }
            out.append(s, i, next);
            String close = block ? "\\]" : "\\)";
            int end = s.indexOf(close, next + 2);
            if (end < 0) {
                // Half-streamed formula: leave the rest for the next frame.
                out.append(s.substring(next));
                break;
            }
            String expr = slashFractions(formatExpr(s.substring(next + 2, end)));
            if (block) {
                // Normalize to exactly one blank line around a display formula.
                while (out.length() > 0 && out.charAt(out.length() - 1) == '\n') {
                    out.deleteCharAt(out.length() - 1);
                }
                if (out.length() > 0) out.append("\n\n");
                for (String line : expr.split("\n", -1)) {
                    if (!line.isEmpty()) {
                        out.append(BLOCK).append(line).append('\n');
                    }
                }
                out.append('\n');
                // Absorb source blank lines after a display formula too.
                int after = end + 2;
                while (after < s.length() && Character.isWhitespace(s.charAt(after))) after++;
                i = after;
                continue;
            } else {
                out.append(expr);
            }
            i = end + 2;
        }
        return out.toString();
    }

    /** Recursive formatter for a single LaTeX expression. */
    public static String formatExpr(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length() && s.charAt(i + 1) == '\\') {
                out.append('\n');
                i += 2;
                continue;
            }
            if (ch == '\\') {
                if (i + 1 >= s.length()) break;
                char n = s.charAt(i + 1);
                if (!Character.isLetter(n)) {
                    String sym = SYMBOLS.get(String.valueOf(n));
                    out.append(sym == null ? String.valueOf(n) : sym);
                    i += 2;
                    continue;
                }
                int start = i + 1;
                int j = start;
                while (j < s.length() && Character.isLetter(s.charAt(j))) j++;
                String cmd = s.substring(start, j);
                int[] pos = {j};
                if ("frac".equals(cmd) || "dfrac".equals(cmd) || "tfrac".equals(cmd)) {
                    String a = group(s, pos);
                    String b = group(s, pos);
                    out.append(FRAC_START).append(formatExpr(a))
                            .append(FRAC_SEP).append(formatExpr(b)).append(FRAC_END);
                } else if ("binom".equals(cmd)) {
                    String a = group(s, pos);
                    String b = group(s, pos);
                    out.append("C(").append(formatExpr(a)).append(",")
                            .append(formatExpr(b)).append(')');
                } else if ("sqrt".equals(cmd)) {
                    String a = group(s, pos);
                    out.append("√(").append(formatExpr(a)).append(')');
                } else if ("text".equals(cmd) || "mathrm".equals(cmd)
                        || "mathbf".equals(cmd) || "mathit".equals(cmd)
                        || "operatorname".equals(cmd)) {
                    out.append(formatExpr(group(s, pos)));
                } else if ("boxed".equals(cmd)) {
                    out.append('[').append(formatExpr(group(s, pos))).append(']');
                } else if ("left".equals(cmd) || "right".equals(cmd)) {
                    skipSpace(s, pos);
                    if (pos[0] < s.length()) {
                        char d = s.charAt(pos[0]);
                        if (d == '\\') {
                            pos[0]++;
                            if (pos[0] < s.length()) {
                                String ds = SYMBOLS.get(String.valueOf(s.charAt(pos[0])));
                                out.append(ds == null ? s.charAt(pos[0]) : ds);
                                pos[0]++;
                            }
                        } else if (d != '.' && d != '|') {
                            out.append(d);
                            pos[0]++;
                        } else if (d == '|') {
                            out.append('|');
                            pos[0]++;
                        }
                    }
                } else if ("quad".equals(cmd) || "qquad".equals(cmd)
                        || "enspace".equals(cmd) || "thinspace".equals(cmd)) {
                    out.append("  ");
                } else if ("begin".equals(cmd) || "end".equals(cmd)) {
                    group(s, pos);
                    out.append('\n');
                } else if (SYMBOLS.containsKey(cmd)) {
                    out.append(SYMBOLS.get(cmd));
                    skipSpace(s, pos);
                } else {
                    // Never leave a backslash in the rendered text.
                    out.append(cmd);
                }
                i = pos[0];
                continue;
            }
            if (ch == '{') {
                int[] pos = {i};
                out.append(formatExpr(group(s, pos)));
                i = pos[0];
                continue;
            }
            if (ch == '}') {
                i++;
                continue;
            }
            if (ch == '^' || ch == '_') {
                int[] pos = {i + 1};
                String a = group(s, pos);
                String simple = formatExpr(a);
                String m = ch == '^' ? superscript(simple) : subscript(simple);
                if (m != null) out.append(m);
                else out.append(ch == '^' ? "^(" : "_(").append(simple).append(')');
                i = pos[0];
                continue;
            }
            if (ch == '&') {
                i++;
                continue;
            }
            out.append(ch);
            i++;
        }
        return out.toString();
    }

    private static void skipSpace(String s, int[] pos) {
        while (pos[0] < s.length() && Character.isWhitespace(s.charAt(pos[0]))) pos[0]++;
    }

    /** Read a braced group, or the next single character. */
    private static String group(String s, int[] pos) {
        skipSpace(s, pos);
        if (pos[0] >= s.length()) return "";
        if (s.charAt(pos[0]) != '{') {
            char c = s.charAt(pos[0]++);
            return String.valueOf(c);
        }
        int start = pos[0] + 1;
        int depth = 1;
        int i = start;
        while (i < s.length() && depth > 0) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            i++;
        }
        pos[0] = i;
        return depth == 0 ? s.substring(start, i - 1) : s.substring(start);
    }

    private static String slashFractions(String s) {
        // Plain "1/2" in a problem statement is a fraction too. Keep it to
        // simple single operands so prose, dates and URLs do not get mangled.
        Pattern p = Pattern.compile("(?<![\\w\\uE001\\uE002\\uE003])"
                + "(\\d+(?:\\.\\d+)?|[A-Za-z])\\s*/\\s*"
                + "(\\d+(?:\\.\\d+)?|[A-Za-z])(?![\\w])");
        Matcher m = p.matcher(s);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(b, Matcher.quoteReplacement(""
                    + FRAC_START + m.group(1) + FRAC_SEP + m.group(2) + FRAC_END));
        }
        m.appendTail(b);
        return b.toString();
    }

    private static String superscript(String s) {
        String map = "0123456789+-=()ni";
        String sup = "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾ⁿⁱ";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            int k = map.indexOf(s.charAt(i));
            if (k < 0) return null;
            b.append(sup.charAt(k));
        }
        return b.toString();
    }

    private static String subscript(String s) {
        String map = "0123456789+-=()aehijklmnoprstuvx";
        String sub = "₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎ₐₑₕᵢⱼₖₗₘₙₒₚᵣₛₜᵤᵥₓ";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            int k = map.indexOf(s.charAt(i));
            if (k < 0) return null;
            b.append(sub.charAt(k));
        }
        return b.toString();
    }
}
