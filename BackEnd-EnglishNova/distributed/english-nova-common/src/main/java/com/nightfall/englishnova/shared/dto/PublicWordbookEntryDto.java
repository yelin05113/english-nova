package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PublicWordbookEntryDto", description = "Public wordbook entry")
public record PublicWordbookEntryDto(
        @Schema(description = "Public vocabulary entry id")
        long publicEntryId,
        @Schema(description = "Sort order inside the public wordbook")
        int sortOrder,
        @Schema(description = "Word")
        String word,
        @Schema(description = "Phonetic")
        String phonetic,
        @Schema(description = "Chinese meaning")
        String meaningCn,
        @Schema(description = "Example sentence")
        String exampleSentence,
        @Schema(description = "BNC rank")
        Integer bncRank,
        @Schema(description = "FRQ rank")
        Integer frqRank,
        @Schema(description = "Wordfreq Zipf score")
        Double wordfreqZipf
) {
}
