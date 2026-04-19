package com.nightfall.englishnova.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SystemModuleDto", description = "System module summary")
public record SystemModuleDto(
        @Schema(description = "Module name")
        String name,
        @Schema(description = "Module responsibility")
        String responsibility,
        @Schema(description = "Module status")
        String status
) {
}
