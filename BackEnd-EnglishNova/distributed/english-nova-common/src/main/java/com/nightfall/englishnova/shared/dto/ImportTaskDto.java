package com.nightfall.englishnova.shared.dto;

import com.nightfall.englishnova.shared.enums.WordImportPlatform;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(name = "ImportTaskDto", description = "Import task summary")
public record ImportTaskDto(
        @Schema(description = "Task id")
        String taskId,
        @Schema(description = "Wordbook id")
        Long wordbookId,
        @Schema(description = "Import platform")
        WordImportPlatform platform,
        @Schema(description = "Source name")
        String sourceName,
        @Schema(description = "Estimated card count")
        int estimatedCards,
        @Schema(description = "Imported card count")
        int importedCards,
        @Schema(description = "Task status")
        String status,
        @Schema(description = "Queue time")
        OffsetDateTime queuedAt,
        @Schema(description = "Finish time")
        OffsetDateTime finishedAt,
        @Schema(description = "Queue name")
        String queueName
) {
}
