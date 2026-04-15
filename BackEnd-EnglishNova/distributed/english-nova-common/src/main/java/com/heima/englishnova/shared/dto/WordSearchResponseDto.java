package com.heima.englishnova.shared.dto;

import java.util.List;

/**
 * 单词搜索响应，分为公开词库和个人词库两组。
 *
 * @param publicHits 公开词库搜索结果
 * @param myHits     个人词库搜索结果
 */
public record WordSearchResponseDto(
        List<SearchHitDto> publicHits,
        List<SearchHitDto> myHits
) {
}
