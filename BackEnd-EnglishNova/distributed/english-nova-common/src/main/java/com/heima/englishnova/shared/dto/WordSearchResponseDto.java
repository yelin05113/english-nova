package com.heima.englishnova.shared.dto;

import java.util.List;

public record WordSearchResponseDto(
        List<SearchHitDto> publicHits,
        List<SearchHitDto> myHits
) {
}
