package com.heima.englishnova.importservice.importer;

import com.heima.englishnova.shared.dto.ImportPresetDto;
import com.heima.englishnova.shared.enums.WordImportPlatform;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 单词导入适配器接口，定义各平台词书解析的标准协议。
 */
public interface WordImportAdapter {

    /**
     * 返回适配器对应的导入平台。
     *
     * @return 导入平台枚举
     */
    WordImportPlatform platform();

    /**
     * 返回导入平台预设信息。
     *
     * @return 导入预设 DTO
     */
    ImportPresetDto preset();

    /**
     * 判断是否支持指定文件名后缀的文件。
     *
     * @param fileName 文件名
     * @return 若支持则返回 true
     */
    default boolean supportsFile(String fileName) {
        return false;
    }

    /**
     * 解析文件并返回标准化的词汇记录列表。
     *
     * @param filePath 文件路径
     * @return 词汇记录列表
     * @throws IOException 当文件读取或解析失败时
     */
    default List<ImportedVocabularyRecord> importEntries(Path filePath) throws IOException {
        throw new IllegalArgumentException(platform().name() + " 暂不支持文件导入");
    }
}
