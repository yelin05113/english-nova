package com.nightfall.englishnova.shared.dto;

import com.nightfall.englishnova.shared.enums.WordImportPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(name = "ImportTaskRequest", description = "Import task creation request")
public record ImportTaskRequest(
        @Schema(description = "Import platform")
        @NotNull
        WordImportPlatform platform,
        @Schema(description = "Source name")
        @NotBlank
        String sourceName,
        @Schema(description = "Estimated card count")
        @Min(1)
        int estimatedCards
) {
}
