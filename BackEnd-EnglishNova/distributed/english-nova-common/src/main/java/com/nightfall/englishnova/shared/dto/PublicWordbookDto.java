package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(name = "PublicWordbookDto", description = "Public wordbook summary")
public record PublicWordbookDto(
        @Schema(description = "Public wordbook id")
        long id,
        @Schema(description = "Public wordbook name")
        String name,
        @Schema(description = "Source name")
        String sourceName,
        @Schema(description = "Source URL")
        String sourceUrl,
        @Schema(description = "License name")
        String licenseName,
        @Schema(description = "License URL")
        String licenseUrl,
        @Schema(description = "ECDICT tag")
        String tag,
        @Schema(description = "Total word count")
        int wordCount,
        @Schema(description = "Whether the current user has subscribed")
        boolean subscribed,
        @Schema(description = "Completed count for the current user")
        int completedCount,
        @Schema(description = "Wrong-word count for the current user")
        int wrongCount,
        @Schema(description = "Daily target count for the current user")
        int dailyTargetCount,
        @Schema(description = "Completed count for the current day")
        int todayCompletedCount,
        @Schema(description = "Next sort order for the current user")
        int nextSortOrder,
        @Schema(description = "Creation time")
        OffsetDateTime createdAt,
        @Schema(description = "Last update time")
        OffsetDateTime updatedAt
) {
}
