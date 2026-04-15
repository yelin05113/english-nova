package com.heima.englishnova.shared.dto;

import com.heima.englishnova.shared.enums.QuizMode;

/**
 * 斩词会话信息。
 *
 * @param id               会话 ID
 * @param wordbookId       词书 ID
 * @param mode             斩词模式
 * @param totalQuestions   总题数
 * @param answeredQuestions 已答题数
 * @param correctAnswers   正确题数
 * @param status           会话状态
 */
public record QuizSessionDto(
        String id,
        long wordbookId,
        QuizMode mode,
        int totalQuestions,
        int answeredQuestions,
        int correctAnswers,
        String status
) {
}
