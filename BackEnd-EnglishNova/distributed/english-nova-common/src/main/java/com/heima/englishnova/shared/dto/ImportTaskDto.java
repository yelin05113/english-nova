package com.heima.englishnova.shared.dto;

import com.heima.englishnova.shared.enums.WordImportPlatform;

import java.time.OffsetDateTime;

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
