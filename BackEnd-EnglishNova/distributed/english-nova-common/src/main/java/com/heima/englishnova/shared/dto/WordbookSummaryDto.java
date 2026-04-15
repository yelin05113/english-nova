package com.heima.englishnova.shared.dto;

import com.heima.englishnova.shared.enums.WordImportPlatform;

import java.time.OffsetDateTime;

/**
 * 词书概要信息。
 *
 * @param id            词书 ID
 * @param name          词书名称
 * @param platform      导入平台
 * @param wordCount     单词总数
 * @param clearedCount  已掌握数量
 * @param pendingCount  待学习数量
 * @param createdAt     创建时间
 */
public record WordbookSummaryDto(
        long id,
        String name,
        WordImportPlatform platform,
        int wordCount,
        int clearedCount,
        int pendingCount,
        OffsetDateTime createdAt
) {
}
