package com.wrist.deepseek.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts spoken Chinese math into compact symbols before LLM correction. */
public final class MathSpeech {
    private static final String CN = "零〇一二两三四五六七八九十百";
    private static final Pattern FRACTION = Pattern.compile(
            "([" + CN + "0-9]+)分之(根号)?([" + CN + "0-9]+)");
    private static final Pattern POWER = Pattern.compile(
            "([A-Za-z0-9]+)\\s*的?\\s*(平方|立方|([" + CN + "0-9n]+)次方)");
    private static final Pattern ANGLE = Pattern.compile("角\\s*([A-Z])");

    private MathSpeech() {
    }

    public static String normalize(String text, String subject) {
        if (text == null || text.isEmpty()) return "";
        boolean math = Subject.MATH.equals(Subject.normalize(subject));
        if (!math && !Subject.AUTO.equals(Subject.normalize(subject))) return text;
        if (!math && !looksLikeMath(text)) return text;

        String s = replaceFractions(text);
        s = replacePowers(s);
        // Common math-dictation homophones seen with watch microphones.
        s = s.replace("设计和", "设集合")
                .replace("真整数", "正整数")
                .replace("人以", "任意")
                .replace("之阶", "之积");
        s = s.replaceAll("[，,]其元素中", "，且元素中");
        s = upperSetVariable(s);
        s = s.replace("大于或等于", "≥").replace("大于等于", "≥")
                .replace("小于或等于", "≤").replace("小于等于", "≤")
                .replace("不等于", "≠").replace("约等于", "≈").replace("等于", "=")
                .replace("正负", "±").replace("无穷大", "∞")
                .replace("平行于", "∥").replace("垂直于", "⊥")
                .replace("因为", "∵").replace("所以", "∴")
                .replace("乘以", "×").replace("除以", "÷")
                .replace("根号下", "√").replace("根号", "√")
                .replace("三角形", "△").replace("竖线", "|")
                .replace("百分号", "%").replace("摄氏度", "°C")
                .replace("大于", ">").replace("小于", "<");
        s = s.replaceAll("(?<=[0-9A-Za-z²³⁰¹⁴⁵⁶⁷⁸⁹])加(?=[0-9A-Za-z(])", "+");
        s = s.replaceAll("(?<=[0-9A-Za-z²³⁰¹⁴⁵⁶⁷⁸⁹])减(?=[0-9A-Za-z(])", "-");
        s = s.replaceAll("(?<=[0-9A-Za-z²³⁰¹⁴⁵⁶⁷⁸⁹])乘(?=[0-9A-Za-z(])", "×");
        s = s.replaceAll("(?<=[0-9A-Za-z²³⁰¹⁴⁵⁶⁷⁸⁹])除(?=[0-9A-Za-z(])", "÷");
        s = ANGLE.matcher(s).replaceAll("∠$1");
        s = s.replaceAll("(\\d+(?:\\.\\d+)?)度", "$1°");
        s = s.replaceAll("\\s*([=<>≤≥≠≈×÷+\\-])\\s*", "$1");
        return s.trim();
    }

    public static boolean looksLikeMath(String text) {
        if (text == null) return false;
        return text.matches("(?s).*(分之|平方|立方|根号|三角形|大于等于|小于等于|约等于|正负|"
                + "乘以|除以|概率|方程|函数|竖线|角[A-Z]).*");
    }

    private static String replaceFractions(String s) {
        Matcher m = FRACTION.matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            int den = toInt(m.group(1));
            int num = toInt(m.group(3));
            if (den <= 0 || num < 0) {
                m.appendReplacement(out, Matcher.quoteReplacement(m.group()));
                continue;
            }
            String value;
            if (den == 100) {
                value = num + "%";
            } else {
                value = (m.group(2) == null ? "" : "√") + num + "/" + den;
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String upperSetVariable(String s) {
        Matcher m = Pattern.compile("集合\\s*([a-z])").matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(
                    "集合" + Character.toUpperCase(m.group(1).charAt(0))));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String replacePowers(String s) {
        Matcher m = POWER.matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String base = m.group(1);
            String token = m.group(2);
            String sup = null;
            if ("平方".equals(token)) sup = "²";
            else if ("立方".equals(token)) sup = "³";
            else if (m.group(3) != null) {
                if ("n".equals(m.group(3))) sup = "ⁿ";
                else {
                    int n = toInt(m.group(3));
                    if (n >= 0) sup = superscript(n);
                }
            }
            if (sup == null) {
                m.appendReplacement(out, Matcher.quoteReplacement(m.group()));
                continue;
            }
            m.appendReplacement(out, Matcher.quoteReplacement(base + sup));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String superscript(int n) {
        String digits = "0123456789";
        String sup = "⁰¹²³⁴⁵⁶⁷⁸⁹";
        StringBuilder b = new StringBuilder();
        for (char c : String.valueOf(n).toCharArray()) {
            int k = digits.indexOf(c);
            if (k < 0) return null;
            b.append(sup.charAt(k));
        }
        return b.toString();
    }

    private static int toInt(String s) {
        if (s == null || s.isEmpty()) return -1;
        try {
            return Integer.parseInt(s);
        } catch (Throwable ignored) {
        }
        int total = 0;
        int num = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int d = digit(c);
            if (d >= 0) {
                num = d;
            } else if (c == '十') {
                total += (num == 0 ? 1 : num) * 10;
                num = 0;
            } else if (c == '百') {
                total += (num == 0 ? 1 : num) * 100;
                num = 0;
            } else {
                return -1;
            }
        }
        return total + num;
    }

    private static int digit(char c) {
        switch (c) {
            case '零':
            case '〇':
                return 0;
            case '一':
                return 1;
            case '二':
            case '两':
                return 2;
            case '三':
                return 3;
            case '四':
                return 4;
            case '五':
                return 5;
            case '六':
                return 6;
            case '七':
                return 7;
            case '八':
                return 8;
            case '九':
                return 9;
            default:
                return -1;
        }
    }
}
