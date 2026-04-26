package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "QuizAnswerResultDto", description = "Quiz answer result")
public record QuizAnswerResultDto(
        @Schema(description = "Whether the answer is correct")
        boolean correct,
        @Schema(description = "Correct option")
        String correctOption,
        @Schema(description = "Remaining question count")
        int remainingQuestions,
        @Schema(description = "Whether the daily target was just completed after this answer")
        boolean dailyTargetJustCompleted,
        @Schema(description = "Updated public wordbook progress snapshot")
        PublicWordbookProgressSnapshotDto publicWordbookProgress,
        @Schema(description = "Updated session summary")
        QuizSessionDto session,
        @Schema(description = "Next question")
        QuizQuestionDto nextQuestion
) {
}
