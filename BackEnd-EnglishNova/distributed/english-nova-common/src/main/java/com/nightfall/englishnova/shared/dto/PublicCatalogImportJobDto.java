package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "PublicCatalogImportJobDto", description = "Public catalog import job")
public record PublicCatalogImportJobDto(
        @Schema(description = "Job id")
        long id,
        @Schema(description = "Source name")
        String sourceName,
        @Schema(description = "Job status")
        String status,
        @Schema(description = "Total words")
        int totalWords,
        @Schema(description = "Processed words")
        int processedWords,
        @Schema(description = "Imported words")
        int importedWords,
        @Schema(description = "Updated words")
        int updatedWords,
        @Schema(description = "Skipped words")
        int skippedWords,
        @Schema(description = "Failed words")
        int failedWords,
        @Schema(description = "Refresh existing public entries")
        boolean refreshExisting,
        @Schema(description = "Batch size")
        int batchSize,
        @Schema(description = "Started at")
        Instant startedAt,
        @Schema(description = "Finished at")
        Instant finishedAt,
        @Schema(description = "Creator user id")
        Long createdByUserId,
        @Schema(description = "Error message")
        String errorMessage,
        @Schema(description = "Created at")
        Instant createdAt,
        @Schema(description = "Updated at")
        Instant updatedAt
) {
}
