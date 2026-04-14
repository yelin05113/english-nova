package com.heima.englishnova.shared.dto;

import java.util.List;

public record PublicCatalogImportRequest(
        List<String> words,
        Boolean refreshExisting
) {
}
