package com.heima.englishnova.shared.dto;

/**
 * 单词详情信息。
 *
 * @param entryId       条目 ID
 * @param ownerUserId   所属用户 ID
 * @param wordbookId    词书 ID
 * @param wordbookName  词书名称
 * @param word          单词
 * @param phonetic      音标
 * @param meaningCn     中文释义
 * @param exampleSentence 例句
 * @param category      分类
 * @param difficulty    难度等级
 * @param visibility    可见性
 * @param source        来源标签
 * @param sourceName    来源名称
 * @param importSource  导入来源
 * @param audioUrl      音频地址
 */
public record WordDetailDto(
        long entryId,
        Long ownerUserId,
        long wordbookId,
        String wordbookName,
        String word,
        String phonetic,
        String meaningCn,
        String exampleSentence,
        String category,
        int difficulty,
        String visibility,
        String source,
        String sourceName,
        String importSource,
        String audioUrl
) {
}
