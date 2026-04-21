package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "WordDetailDto", description = "Word detail")
public record WordDetailDto(
        @Schema(description = "Entry id")
        long entryId,
        @Schema(description = "Entry type")
        String entryType,
        @Schema(description = "Owner user id")
        Long ownerUserId,
        @Schema(description = "Wordbook id")
        Long wordbookId,
        @Schema(description = "Wordbook name")
        String wordbookName,
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
        @Schema(description = "BNC rank")
        Integer bncRank,
        @Schema(description = "FRQ rank")
        Integer frqRank,
        @Schema(description = "Wordfreq Zipf score")
        Double wordfreqZipf,
        @Schema(description = "Exchange info")
        String exchangeInfo,
        @Schema(description = "Data quality")
        String dataQuality,
        @Schema(description = "Difficulty level")
        Integer difficulty,
        @Schema(description = "Visibility")
        String visibility,
        @Schema(description = "Source label")
        String source,
        @Schema(description = "Source name")
        String sourceName,
        @Schema(description = "Import source")
        String importSource,
        @Schema(description = "Audio url")
        String audioUrl
) {
}
