package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "PublicCatalogImportResultDto", description = "Public catalog import result")
public record PublicCatalogImportResultDto(
        @Schema(description = "Requested word count")
        int requestedWords,
        @Schema(description = "Imported word count")
        int importedWords,
        @Schema(description = "Updated word count")
        int updatedWords,
        @Schema(description = "Skipped word count")
        int skippedWords,
        @Schema(description = "Failed word count")
        int failedWords,
        @Schema(description = "Imported words")
        List<String> imported,
        @Schema(description = "Updated words")
        List<String> updated,
        @Schema(description = "Skipped words")
        List<String> skipped,
        @Schema(description = "Failed words")
        List<String> failed
) {
}
