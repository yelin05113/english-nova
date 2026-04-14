package com.heima.englishnova.shared.dto;

public record SearchSuggestionDto(
        Long entryId,
        String word,
        String visibility,
        int matchPercent,
        String matchType
) {
}
