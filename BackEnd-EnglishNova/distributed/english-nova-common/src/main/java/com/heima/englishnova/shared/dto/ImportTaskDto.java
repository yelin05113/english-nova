package com.heima.englishnova.shared.dto;

import com.heima.englishnova.shared.enums.WordImportPlatform;

import java.time.OffsetDateTime;

/**
 * 导入任务信息。
 *
 * @param taskId        任务 ID
 * @param wordbookId    词书 ID
 * @param platform      导入平台
 * @param sourceName    来源名称
 * @param estimatedCards 预估单词数量
 * @param importedCards 已导入单词数量
 * @param status        任务状态
 * @param queuedAt      入队时间
 * @param finishedAt    完成时间
 * @param queueName     队列名称
 */
public record ImportTaskDto(
        String taskId,
        Long wordbookId,
        WordImportPlatform platform,
        String sourceName,
        int estimatedCards,
        int importedCards,
        String status,
        OffsetDateTime queuedAt,
        OffsetDateTime finishedAt,
        String queueName
) {
}
