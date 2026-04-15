package com.heima.englishnova.shared.dto;

import java.util.List;

/**
 * 公共词库导入结果。
 *
 * @param requestedWords 请求导入的单词数
 * @param importedWords  新导入的单词数
 * @param updatedWords   更新的单词数
 * @param skippedWords   跳过的单词数
 * @param failedWords    失败的单词数
 * @param imported       新导入的单词列表
 * @param updated        更新的单词列表
 * @param skipped        跳过的单词列表
 * @param failed         失败的单词列表
 */
public record PublicCatalogImportResultDto(
        int requestedWords,
        int importedWords,
        int updatedWords,
        int skippedWords,
        int failedWords,
        List<String> imported,
        List<String> updated,
        List<String> skipped,
        List<String> failed
) {
}
