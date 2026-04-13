package com.heima.englishnova.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record QuizAnswerRequest(
        @NotNull(message = "题目不能为空")
        Long attemptId,
        @NotBlank(message = "答案不能为空")
        String selectedOption
) {
}
