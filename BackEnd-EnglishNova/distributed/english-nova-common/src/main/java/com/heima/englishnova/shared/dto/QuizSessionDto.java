package com.heima.englishnova.shared.dto;

import com.heima.englishnova.shared.enums.QuizMode;

public record QuizSessionDto(
        String id,
        long wordbookId,
        QuizMode mode,
        int totalQuestions,
        int answeredQuestions,
        int correctAnswers,
        String status
) {
}
