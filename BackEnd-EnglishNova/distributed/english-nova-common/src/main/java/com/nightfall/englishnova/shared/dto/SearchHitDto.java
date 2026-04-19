package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SearchHitDto", description = "Search hit")
public record SearchHitDto(
        @Schema(description = "Entry id")
        Long entryId,
        @Schema(description = "Word")
        String word,
        @Schema(description = "Phonetic")
        String phonetic,
        @Schema(description = "Chinese meaning")
        String meaningCn,
        @Schema(description = "Source label")
        String source,
        @Schema(description = "Example sentence")
        String exampleSentence,
        @Schema(description = "Category")
        String category,
        @Schema(description = "English definition")
        String definitionEn,
        @Schema(description = "Tags")
        String tags,
        @Schema(description = "Frequency rank")
        Integer frequencyRank,
        @Schema(description = "Wordfreq Zipf score")
        Double wordfreqZipf,
        @Schema(description = "Data quality")
        String dataQuality,
        @Schema(description = "Visibility")
        String visibility,
        @Schema(description = "Import source")
        String importSource,
        @Schema(description = "Match percent")
        int matchPercent,
        @Schema(description = "Match type")
        String matchType
) {
}
