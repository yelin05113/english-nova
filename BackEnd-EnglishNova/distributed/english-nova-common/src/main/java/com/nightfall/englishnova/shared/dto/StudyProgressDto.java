package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "StudyProgressDto", description = "Study progress summary")
public record StudyProgressDto(
        @Schema(description = "Total word count")
        int totalWords,
        @Schema(description = "Cleared word count")
        int clearedWords,
        @Schema(description = "In progress word count")
        int inProgressWords,
        @Schema(description = "New word count")
        int newWords,
        @Schema(description = "Wordbook count")
        int wordbooks,
        @Schema(description = "Answered question count")
        int answeredQuestions,
        @Schema(description = "Correct answer count")
        int correctAnswers,
        @Schema(description = "Accuracy rate percentage")
        int accuracyRate
) {
}
