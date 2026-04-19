package com.nightfall.englishnova.shared.dto;

import com.nightfall.englishnova.shared.enums.QuizMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "CreateQuizSessionRequest", description = "Quiz session creation request")
public record CreateQuizSessionRequest(
        @Schema(description = "Wordbook id")
        @NotNull(message = "Wordbook cannot be null")
        Long wordbookId,
        @Schema(description = "Quiz mode")
        QuizMode mode
) {
}
