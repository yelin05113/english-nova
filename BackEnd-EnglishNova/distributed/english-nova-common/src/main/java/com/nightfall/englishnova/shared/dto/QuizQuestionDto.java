package com.nightfall.englishnova.shared.dto;

import com.nightfall.englishnova.shared.enums.VocabularyEntryType;
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
        @Schema(description = "Current vocabulary meaning")
        String meaningCn,
        @Schema(description = "Prompt phonetic")
        String phonetic,
        @Schema(description = "Prompt audio URL")
        String audioUrl,
        @Schema(description = "Original example sentence")
        String exampleSentence,
        @Schema(description = "Corrected example sentence")
        String correctedExampleSentence,
        @Schema(description = "Chinese translation of the example sentence")
        String chineseSentence,
        @Schema(description = "Example sentence audio URL")
        String exampleAudioUrl,
        @Schema(description = "Current vocabulary source entry type")
        VocabularyEntryType sourceEntryType,
        @Schema(description = "Current vocabulary source entry id")
        Long sourceEntryId,
        @Schema(description = "Available options")
        List<String> options,
        @Schema(description = "Available option display details")
        List<QuizOptionDto> optionDetails,
        @Schema(description = "Current progress number")
        int progress,
        @Schema(description = "Total number of questions")
        int totalQuestions
) {
}
