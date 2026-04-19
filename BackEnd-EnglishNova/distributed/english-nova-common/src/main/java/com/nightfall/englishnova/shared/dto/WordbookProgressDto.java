package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "WordbookProgressDto", description = "Wordbook progress summary")
public record WordbookProgressDto(
        @Schema(description = "Wordbook id")
        long wordbookId,
        @Schema(description = "Total word count")
        int wordCount,
        @Schema(description = "Cleared count")
        int clearedCount,
        @Schema(description = "In progress count")
        int inProgressCount,
        @Schema(description = "Pending count")
        int pendingCount
) {
}
