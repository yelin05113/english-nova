package com.heima.englishnova.shared.dto;

import com.heima.englishnova.shared.enums.QuizMode;
import jakarta.validation.constraints.NotNull;

public record CreateQuizSessionRequest(
        @NotNull(message = "词书不能为空")
        Long wordbookId,
        QuizMode mode
) {
}
