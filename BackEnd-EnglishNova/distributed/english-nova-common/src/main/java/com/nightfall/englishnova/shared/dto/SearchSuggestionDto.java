package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SearchSuggestionDto", description = "Search suggestion")
public record SearchSuggestionDto(
        @Schema(description = "Entry id")
        Long entryId,
        @Schema(description = "Word")
        String word,
        @Schema(description = "Visibility")
        String visibility,
        @Schema(description = "Match percent")
        int matchPercent,
        @Schema(description = "Match type")
        String matchType
) {
}
