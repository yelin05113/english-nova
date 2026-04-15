package com.heima.englishnova.shared.dto;

/**
 * 斩词会话状态，包含当前会话和下一道题目。
 *
 * @param session         会话信息
 * @param currentQuestion 当前题目
 */
public record QuizSessionStateDto(
        QuizSessionDto session,
        QuizQuestionDto currentQuestion
) {
}
