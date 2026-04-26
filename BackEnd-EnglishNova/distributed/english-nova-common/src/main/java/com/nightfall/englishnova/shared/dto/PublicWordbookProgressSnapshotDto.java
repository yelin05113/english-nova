package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PublicWordbookProgressSnapshotDto", description = "Public wordbook progress snapshot")
public record PublicWordbookProgressSnapshotDto(
        @Schema(description = "Public wordbook id")
        long publicWordbookId,
        @Schema(description = "Completed word count")
        int completedCount,
        @Schema(description = "Daily target count")
        int dailyTargetCount,
        @Schema(description = "Today's completed count")
        int todayCompletedCount,
        @Schema(description = "Total word count")
        int wordCount
) {
}
