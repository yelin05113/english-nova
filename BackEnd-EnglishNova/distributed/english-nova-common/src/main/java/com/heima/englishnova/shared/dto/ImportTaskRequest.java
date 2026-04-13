package com.heima.englishnova.shared.dto;

import com.heima.englishnova.shared.enums.WordImportPlatform;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ImportTaskRequest(
        @NotNull WordImportPlatform platform,
        @NotBlank String sourceName,
        @Min(1) int estimatedCards
) {
}
