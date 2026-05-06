package com.nightfall.englishnova.shared.dto;

import com.nightfall.englishnova.shared.enums.VocabularyEntryType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(name = "WordNotebookEntryDto", description = "Word notebook entry")
public record WordNotebookEntryDto(
        @Schema(description = "Notebook entry id")
        long id,
        @Schema(description = "Word notebook id")
        long notebookId,
        @Schema(description = "Normalized word key")
        String normalizedWord,
        @Schema(description = "Word")
        String word,
        @Schema(description = "Phonetic")
        String phonetic,
        @Schema(description = "Chinese meaning")
        String meaningCn,
        @Schema(description = "Original example sentence")
        String exampleSentence,
        @Schema(description = "Corrected example sentence")
        String correctedExampleSentence,
        @Schema(description = "Chinese sentence")
        String chineseSentence,
        @Schema(description = "Example audio url")
        String exampleAudioUrl,
        @Schema(description = "Source entry type")
        VocabularyEntryType sourceEntryType,
        @Schema(description = "Source entry id")
        Long sourceEntryId,
        @Schema(description = "Creation time")
        OffsetDateTime createdAt
) {
}
