package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "PublicCatalogImportRequest", description = "Public catalog import request")
public record PublicCatalogImportRequest(
        @Schema(description = "Words to import")
        List<String> words,
        @Schema(description = "Whether to refresh existing entries")
        Boolean refreshExisting
) {
}
