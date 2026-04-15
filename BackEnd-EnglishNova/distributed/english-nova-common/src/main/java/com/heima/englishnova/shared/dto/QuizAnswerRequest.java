package com.heima.englishnova.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 斩词作答请求。
 *
 * @param attemptId      作答记录 ID
 * @param selectedOption 选择的选项
 */
public record QuizAnswerRequest(
        @NotNull(message = "题目不能为空")
        Long attemptId,
        @NotBlank(message = "答案不能为空")
        String selectedOption
) {
}
