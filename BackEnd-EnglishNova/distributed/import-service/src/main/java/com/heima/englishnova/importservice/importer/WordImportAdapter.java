package com.heima.englishnova.importservice.importer;

import com.heima.englishnova.shared.dto.ImportPresetDto;
import com.heima.englishnova.shared.enums.WordImportPlatform;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface WordImportAdapter {

    WordImportPlatform platform();

    ImportPresetDto preset();

    default boolean supportsFile(String fileName) {
        return false;
    }

    default List<ImportedVocabularyRecord> importEntries(Path filePath) throws IOException {
        throw new IllegalArgumentException(platform().name() + " 暂不支持文件导入");
    }
}
