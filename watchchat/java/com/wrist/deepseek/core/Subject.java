package com.wrist.deepseek.core;

/** Subject presets used by voice correction and by the math speech normalizer. */
public final class Subject {
    public static final String AUTO = "auto";
    public static final String MATH = "math";
    public static final String CHINESE = "chinese";
    public static final String GEOGRAPHY = "geography";
    public static final String OTHER = "other";
    public static final String OFF = "off";

    private Subject() {
    }

    public static String normalize(String mode) {
        if (MATH.equals(mode) || CHINESE.equals(mode)
                || GEOGRAPHY.equals(mode) || OTHER.equals(mode)
                || OFF.equals(mode)) return mode;
        return AUTO;
    }

    public static String label(String mode) {
        switch (normalize(mode)) {
            case MATH:
                return "数学";
            case CHINESE:
                return "语文";
            case GEOGRAPHY:
                return "地理";
            case OTHER:
                return "通用";
            case OFF:
                return "关闭";
            default:
                return "自动";
        }
    }

    public static String correctionRules(String mode) {
        switch (normalize(mode)) {
            case MATH:
                return "当前是数学题语音。识别结果可能有严重同音错误，例如："
                        + "‘设集合A’被听成‘设计和a’，‘正整数’被听成‘真整数’，"
                        + "‘且’被听成‘其’，‘任意’被听成‘人以’，‘之积’被听成‘之阶’。"
                        + "请根据数学题意还原完整题干，不要只做逐字替换；"
                        + "优先修正数学术语、数字、单位、括号和公式符号；"
                        + "保留已经规范化的 1/2、x²、√3、△ABC、≥、∵ 等写法，不要改回中文口语；"
                        + "不要展开成解题过程，只输出用户说的题干或问题。";
            case CHINESE:
                return "当前是语文语音。优先保留诗句、作者、朝代、成语、文言词和修辞术语；"
                        + "不要改写用户的原意或把诗句改成现代白话。";
            case GEOGRAPHY:
                return "当前是地理语音。优先修正地形、气候、洋流、板块、经纬度、区域和人文地理术语；"
                        + "数字和单位要与地理常识一致。";
            case OTHER:
                return "当前是通用语音。只修正同音字、错别字、断句和标点，保留原意。";
            case OFF:
                return "学科增强已关闭。只按通用语音纠错规则处理，不做学科术语增强和数学公式转换。";
            default:
                return "自动判断学科。遇到数学、语文或地理术语时优先采用正确写法；"
                        + "数学题中若出现‘设计和’‘真整数’‘人以’‘之阶’等明显同音错误，"
                        + "应按题意还原；不确定时不要添加原话没有的信息。";
        }
    }

    public static String lexicon(String mode) {
        switch (normalize(mode)) {
            case MATH:
                return "函数、导数、概率、条件概率、向量、三角形、相似、全等、平行、垂直、"
                        + "方程、不等式、分数、小数、百分数、周长、面积、体积、半径、直径、"
                        + "正弦、余弦、正切、等差数列、等比数列、余数、因数、倍数";
            case CHINESE:
                return "作者、朝代、诗人、词牌、文言文、通假字、古今异义、中心思想、段落大意、"
                        + "比喻、拟人、排比、借代、诗句、成语、修辞手法";
            case GEOGRAPHY:
                return "等高线、季风、洋流、板块构造、喀斯特、地中海气候、温带海洋性气候、"
                        + "时区、经线、纬线、地形、地貌、人口密度、城市化、产业转移";
            case OTHER:
                return "无特定学科术语";
            case OFF:
                return "无（学科增强已关闭）";
            default:
                return "数学、语文、地理等学科术语";
        }
    }
}
