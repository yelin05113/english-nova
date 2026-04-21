package com.nightfall.englishnova.shared.dto;

import com.nightfall.englishnova.shared.enums.QuizMode;
import com.nightfall.englishnova.shared.enums.QuizTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "CreateQuizSessionRequest", description = "Quiz session creation request")
public record CreateQuizSessionRequest(
        @Schema(description = "Target type")
        @NotNull(message = "Target type cannot be null")
        QuizTargetType targetType,
        @Schema(description = "Target id")
        @NotNull(message = "Target id cannot be null")
        Long targetId,
        @Schema(description = "Quiz mode")
        QuizMode mode
) {
}
