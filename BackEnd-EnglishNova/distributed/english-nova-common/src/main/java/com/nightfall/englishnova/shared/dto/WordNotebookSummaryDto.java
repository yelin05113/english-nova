package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(name = "WordNotebookSummaryDto", description = "Word notebook summary")
public record WordNotebookSummaryDto(
        @Schema(description = "Word notebook id")
        long id,
        @Schema(description = "Word notebook name")
        String name,
        @Schema(description = "Collected word count")
        int wordCount,
        @Schema(description = "Whether the queried word is already in this notebook")
        boolean containsWord,
        @Schema(description = "Creation time")
        OffsetDateTime createdAt
) {
}
