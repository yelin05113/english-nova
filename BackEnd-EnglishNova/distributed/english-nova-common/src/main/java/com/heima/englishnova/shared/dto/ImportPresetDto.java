package com.heima.englishnova.shared.dto;

import com.heima.englishnova.shared.enums.WordImportPlatform;

import java.util.List;

public record ImportPresetDto(
        WordImportPlatform platform,
        String title,
        String description,
        List<String> acceptedExtensions,
        List<String> mappedFields
) {
}
