package com.heima.englishnova.shared.dto;

public record QuizAnswerResultDto(
        boolean correct,
        String correctOption,
        int remainingQuestions,
        QuizSessionDto session,
        QuizQuestionDto nextQuestion
) {
}
