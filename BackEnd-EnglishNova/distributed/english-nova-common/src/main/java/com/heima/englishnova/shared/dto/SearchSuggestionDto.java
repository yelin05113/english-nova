package com.heima.englishnova.shared.dto;

/**
 * 搜索建议条目。
 *
 * @param entryId      条目 ID
 * @param word         单词
 * @param visibility   可见性
 * @param matchPercent 匹配百分比
 * @param matchType    匹配类型
 */
public record SearchSuggestionDto(
        Long entryId,
        String word,
        String visibility,
        int matchPercent,
        String matchType
) {
}
