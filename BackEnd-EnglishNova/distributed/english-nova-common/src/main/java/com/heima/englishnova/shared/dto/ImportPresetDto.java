package com.heima.englishnova.shared.dto;

import com.heima.englishnova.shared.enums.WordImportPlatform;

import java.util.List;

/**
 * 导入平台预设信息。
 *
 * @param platform           导入平台
 * @param title               预设标题
 * @param description         预设描述
 * @param acceptedExtensions  支持的文件扩展名列表
 * @param mappedFields        映射字段列表
 */
public record ImportPresetDto(
        WordImportPlatform platform,
        String title,
        String description,
        List<String> acceptedExtensions,
        List<String> mappedFields
) {
}
