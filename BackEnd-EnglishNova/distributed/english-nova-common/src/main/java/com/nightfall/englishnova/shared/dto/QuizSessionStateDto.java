package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "QuizSessionStateDto", description = "Quiz session state")
public record QuizSessionStateDto(
        @Schema(description = "Session summary")
        QuizSessionDto session,
        @Schema(description = "Current question")
        QuizQuestionDto currentQuestion
) {
}
