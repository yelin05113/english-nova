package com.heima.englishnova.shared.dto;

import com.heima.englishnova.shared.enums.PromptType;

import java.util.List;

public record QuizQuestionDto(
        long attemptId,
        PromptType promptType,
        String promptText,
        List<String> options,
        int progress,
        int totalQuestions
) {
}
