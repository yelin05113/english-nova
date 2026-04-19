package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "VocabularyEntryDto", description = "Vocabulary entry")
public record VocabularyEntryDto(
        @Schema(description = "Entry id")
        long id,
        @Schema(description = "Word")
        String word,
        @Schema(description = "Phonetic")
        String phonetic,
        @Schema(description = "Chinese meaning")
        String meaningCn,
        @Schema(description = "Example sentence")
        String exampleSentence,
        @Schema(description = "Category")
        String category,
        @Schema(description = "Difficulty level")
        int difficulty,
        @Schema(description = "Visibility")
        String visibility
) {
}
