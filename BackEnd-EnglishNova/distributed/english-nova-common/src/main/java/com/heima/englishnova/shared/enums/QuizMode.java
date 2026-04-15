package com.heima.englishnova.shared.enums;

/**
 * 斩词模式枚举，决定题目出题方向。
 */
public enum QuizMode {
    /** 中译英，给出中文选出英文。 */
    CN_TO_EN,
    /** 英译中，给出英文选中释义。 */
    EN_TO_CN,
    /** 混合模式，交替出题。 */
    MIXED
}
