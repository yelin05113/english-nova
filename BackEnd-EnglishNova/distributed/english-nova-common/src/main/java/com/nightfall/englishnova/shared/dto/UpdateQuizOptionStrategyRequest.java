package com.nightfall.englishnova.shared.dto;

import com.nightfall.englishnova.shared.enums.QuizOptionStrategy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "UpdateQuizOptionStrategyRequest", description = "Quiz option strategy preference update request")
public record UpdateQuizOptionStrategyRequest(
        @Schema(description = "Quiz option strategy")
        @NotNull(message = "Quiz option strategy cannot be null")
        QuizOptionStrategy quizOptionStrategy
) {
}
