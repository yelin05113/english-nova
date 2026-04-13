package com.heima.englishnova.shared.dto;

import java.util.List;

public record SystemOverviewDto(
        String productName,
        String theme,
        List<String> supportedPlatforms,
        List<SystemModuleDto> modules,
        List<String> deliveryPhases
) {
}
