package com.heima.englishnova.shared.dto;

import com.heima.englishnova.shared.enums.WordImportPlatform;

import java.time.OffsetDateTime;

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
