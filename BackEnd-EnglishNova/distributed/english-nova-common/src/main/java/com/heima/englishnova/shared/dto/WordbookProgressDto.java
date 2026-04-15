package com.heima.englishnova.shared.dto;

/**
 * 词书学习进度信息。
 *
 * @param wordbookId      词书 ID
 * @param wordCount       单词总数
 * @param clearedCount    已掌握数量
 * @param inProgressCount 进行中数量
 * @param pendingCount    待学习数量
 */
public record WordbookProgressDto(
        long wordbookId,
        int wordCount,
        int clearedCount,
        int inProgressCount,
        int pendingCount
) {
}
