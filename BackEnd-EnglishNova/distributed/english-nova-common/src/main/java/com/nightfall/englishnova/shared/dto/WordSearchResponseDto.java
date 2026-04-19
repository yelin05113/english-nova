package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "WordSearchResponseDto", description = "Word search response")
public record WordSearchResponseDto(
        @Schema(description = "Public catalog hits")
        List<SearchHitDto> publicHits,
        @Schema(description = "Private wordbook hits")
        List<SearchHitDto> myHits
) {
}
