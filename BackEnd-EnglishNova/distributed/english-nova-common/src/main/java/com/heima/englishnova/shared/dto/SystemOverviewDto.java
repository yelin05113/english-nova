package com.heima.englishnova.shared.dto;

import java.util.List;

/**
 * 系统概览信息。
 *
 * @param productName       产品名称
 * @param theme             产品主题
 * @param supportedPlatforms 支持的平台列表
 * @param modules           模块列表
 * @param deliveryPhases    交付阶段列表
 */
public record SystemOverviewDto(
        String productName,
        String theme,
        List<String> supportedPlatforms,
        List<SystemModuleDto> modules,
        List<String> deliveryPhases
) {
}
