package com.heima.englishnova.shared.dto;

public record StudyProgressDto(
        int totalWords,
        int clearedWords,
        int inProgressWords,
        int newWords,
        int wordbooks,
        int answeredQuestions,
        int correctAnswers,
        int accuracyRate
) {
}
