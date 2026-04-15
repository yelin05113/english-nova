package com.heima.englishnova.importservice.importer.adapter;

import com.heima.englishnova.importservice.importer.WordImportAdapter;
import com.heima.englishnova.shared.dto.ImportPresetDto;
import com.heima.englishnova.shared.enums.WordImportPlatform;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bubeidanci（不背单词）导入适配器，声明支持的平台与文件格式预设。
 */
@Component
public class BubeidanciWordImportAdapter implements WordImportAdapter {

    /**
     * 返回适配器对应的导入平台。
     *
     * @return {@link WordImportPlatform#BUBEIDANCI}
     */
    @Override
    public WordImportPlatform platform() {
        return WordImportPlatform.BUBEIDANCI;
    }

    /**
     * 返回 Bubeidanci 导入预设信息。
     *
     * @return 导入预设 DTO
     */
    @Override
    public ImportPresetDto preset() {
        return new ImportPresetDto(
                platform(),
                "Bubeidanci Export",
                "Ingest CSV or TXT bundles and keep familiarity state and review timing.",
                List.of("csv", "txt", "json"),
                List.of("word", "meaning", "memoryHint", "familiarity", "reviewAt")
        );
    }
}
