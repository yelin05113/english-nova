package com.nightfall.englishnova.shared.dto;

import com.nightfall.englishnova.shared.enums.WordImportPlatform;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(name = "WordbookSummaryDto", description = "Wordbook summary")
public record WordbookSummaryDto(
        @Schema(description = "Wordbook id")
        long id,
        @Schema(description = "Wordbook name")
        String name,
        @Schema(description = "Import platform")
        WordImportPlatform platform,
        @Schema(description = "Total word count")
        int wordCount,
        @Schema(description = "Cleared count")
        int clearedCount,
        @Schema(description = "Pending count")
        int pendingCount,
        @Schema(description = "Creation time")
        OffsetDateTime createdAt
) {
}
