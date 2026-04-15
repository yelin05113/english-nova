package com.heima.englishnova.shared.dto;

/**
 * 搜索命中条目。
 *
 * @param entryId      条目 ID
 * @param word         单词
 * @param phonetic     音标
 * @param meaningCn    中文释义
 * @param source       来源标签
 * @param exampleSentence 例句
 * @param category     分类
 * @param visibility   可见性
 * @param importSource 导入来源
 * @param matchPercent 匹配百分比
 * @param matchType    匹配类型
 */
public record SearchHitDto(
        Long entryId,
        String word,
        String phonetic,
        String meaningCn,
        String source,
        String exampleSentence,
        String category,
        String visibility,
        String importSource,
        int matchPercent,
        String matchType
) {
}
