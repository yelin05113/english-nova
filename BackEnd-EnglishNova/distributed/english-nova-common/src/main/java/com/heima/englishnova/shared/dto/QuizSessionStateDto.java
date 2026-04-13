package com.heima.englishnova.shared.dto;

public record QuizSessionStateDto(
        QuizSessionDto session,
        QuizQuestionDto currentQuestion
) {
}
