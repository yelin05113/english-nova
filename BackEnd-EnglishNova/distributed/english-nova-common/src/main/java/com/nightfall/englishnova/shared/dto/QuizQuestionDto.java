package com.nightfall.englishnova.shared.dto;

import com.nightfall.englishnova.shared.enums.PromptType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "QuizQuestionDto", description = "Quiz question")
public record QuizQuestionDto(
        @Schema(description = "Attempt id")
        long attemptId,
        @Schema(description = "Prompt type")
        PromptType promptType,
        @Schema(description = "Prompt text")
        String promptText,
        @Schema(description = "Current vocabulary word")
        String currentWord,
        @Schema(description = "Prompt phonetic")
        String phonetic,
        @Schema(description = "Prompt audio URL")
        String audioUrl,
        @Schema(description = "Available options")
        List<String> options,
        @Schema(description = "Current progress number")
        int progress,
        @Schema(description = "Total number of questions")
        int totalQuestions
) {
}
