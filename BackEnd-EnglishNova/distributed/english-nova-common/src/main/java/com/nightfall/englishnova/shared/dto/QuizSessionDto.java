package com.nightfall.englishnova.shared.dto;

import com.nightfall.englishnova.shared.enums.QuizMode;
import com.nightfall.englishnova.shared.enums.QuizTargetType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "QuizSessionDto", description = "Quiz session summary")
public record QuizSessionDto(
        @Schema(description = "Session id")
        String id,
        @Schema(description = "Legacy wordbook id alias")
        long wordbookId,
        @Schema(description = "Target type")
        QuizTargetType targetType,
        @Schema(description = "Target id")
        long targetId,
        @Schema(description = "Quiz mode")
        QuizMode mode,
        @Schema(description = "Total number of questions")
        int totalQuestions,
        @Schema(description = "Answered question count")
        int answeredQuestions,
        @Schema(description = "Correct answer count")
        int correctAnswers,
        @Schema(description = "Today's correct answer attempt count")
        int todayCorrectAttempts,
        @Schema(description = "Today's total answer attempt count")
        int todayTotalAttempts,
        @Schema(description = "Session status")
        String status
) {
}
