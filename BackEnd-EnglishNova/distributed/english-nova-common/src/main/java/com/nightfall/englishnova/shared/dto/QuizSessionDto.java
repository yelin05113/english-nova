package com.nightfall.englishnova.shared.dto;

import com.nightfall.englishnova.shared.enums.QuizMode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "QuizSessionDto", description = "Quiz session summary")
public record QuizSessionDto(
        @Schema(description = "Session id")
        String id,
        @Schema(description = "Wordbook id")
        long wordbookId,
        @Schema(description = "Quiz mode")
        QuizMode mode,
        @Schema(description = "Total number of questions")
        int totalQuestions,
        @Schema(description = "Answered question count")
        int answeredQuestions,
        @Schema(description = "Correct answer count")
        int correctAnswers,
        @Schema(description = "Session status")
        String status
) {
}
