package com.heima.englishnova.shared.dto;

import java.util.List;

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
