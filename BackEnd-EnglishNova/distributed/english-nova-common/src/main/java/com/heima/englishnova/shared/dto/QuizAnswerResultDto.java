package com.heima.englishnova.shared.dto;

/**
 * 斩词作答结果。
 *
 * @param correct            是否正确
 * @param correctOption      正确选项
 * @param remainingQuestions 剩余题数
 * @param session            会话信息
 * @param nextQuestion       下一题信息
 */
public record QuizAnswerResultDto(
        boolean correct,
        String correctOption,
        int remainingQuestions,
        QuizSessionDto session,
        QuizQuestionDto nextQuestion
) {
}
