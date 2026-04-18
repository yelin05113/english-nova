package com.nightfall.englishnova.shared.dto;

import com.nightfall.englishnova.shared.enums.WordImportPlatform;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "ImportPresetDto", description = "Import preset summary")
public record ImportPresetDto(
        @Schema(description = "Import platform")
        WordImportPlatform platform,
        @Schema(description = "Preset title")
        String title,
        @Schema(description = "Preset description")
        String description,
        @Schema(description = "Accepted file extensions")
        List<String> acceptedExtensions,
        @Schema(description = "Mapped field names")
        List<String> mappedFields
) {
}
