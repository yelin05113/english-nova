package com.heima.englishnova.shared.dto;

import com.heima.englishnova.shared.enums.PromptType;

import java.util.List;

/**
 * 斩词题目信息。
 *
 * @param attemptId      作答记录 ID
 * @param promptType     提示类型
 * @param promptText     题目文本
 * @param options        选项列表
 * @param progress       当前提号
 * @param totalQuestions 总题数
 */
public record QuizQuestionDto(
        long attemptId,
        PromptType promptType,
        String promptText,
        List<String> options,
        int progress,
        int totalQuestions
) {
}
