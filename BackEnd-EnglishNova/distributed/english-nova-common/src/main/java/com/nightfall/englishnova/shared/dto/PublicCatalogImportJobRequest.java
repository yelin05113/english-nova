package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PublicCatalogImportJobRequest", description = "Public catalog import job request")
public record PublicCatalogImportJobRequest(
        @Schema(description = "Source name")
        String sourceName,
        @Schema(description = "Word limit")
        Integer limit,
        @Schema(description = "Batch size")
        Integer batchSize,
        @Schema(description = "Refresh existing public entries")
        Boolean refreshExisting
) {
}
