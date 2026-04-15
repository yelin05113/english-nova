package com.heima.englishnova.shared.dto;

/**
 * 系统模块信息。
 *
 * @param name         模块名称
 * @param responsibility 模块职责
 * @param status       模块状态
 */
public record SystemModuleDto(
        String name,
        String responsibility,
        String status
) {
}
