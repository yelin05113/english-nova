package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "SystemOverviewDto", description = "System overview")
public record SystemOverviewDto(
        @Schema(description = "Product name")
        String productName,
        @Schema(description = "Theme")
        String theme,
        @Schema(description = "Supported platforms")
        List<String> supportedPlatforms,
        @Schema(description = "System modules")
        List<SystemModuleDto> modules,
        @Schema(description = "Delivery phases")
        List<String> deliveryPhases
) {
}
