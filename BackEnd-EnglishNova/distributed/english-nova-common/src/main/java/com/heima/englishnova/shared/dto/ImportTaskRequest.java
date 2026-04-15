package com.heima.englishnova.shared.dto;

import com.heima.englishnova.shared.enums.WordImportPlatform;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建导入任务请求。
 *
 * @param platform       导入平台
 * @param sourceName     来源名称
 * @param estimatedCards 预估单词数量
 */
public record ImportTaskRequest(
        @NotNull WordImportPlatform platform,
        @NotBlank String sourceName,
        @Min(1) int estimatedCards
) {
}
