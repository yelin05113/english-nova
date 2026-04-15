package com.heima.englishnova.importservice.importer.adapter;

import com.heima.englishnova.importservice.importer.WordImportAdapter;
import com.heima.englishnova.shared.dto.ImportPresetDto;
import com.heima.englishnova.shared.enums.WordImportPlatform;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 扇贝单词（Shanbay）导入适配器，声明支持的平台与文件格式预设。
 */
@Component
public class ShanbayWordImportAdapter implements WordImportAdapter {

    /**
     * 返回适配器对应的导入平台。
     *
     * @return {@link WordImportPlatform#SHANBAY}
     */
    @Override
    public WordImportPlatform platform() {
        return WordImportPlatform.SHANBAY;
    }

    /**
     * 返回 Shanbay 导入预设信息。
     *
     * @return 导入预设 DTO
     */
    @Override
    public ImportPresetDto preset() {
        return new ImportPresetDto(
                platform(),
                "Shanbay Export",
                "Map example sentences, derived forms, and category labels into import tasks.",
                List.of("csv", "json"),
                List.of("word", "meaning", "example", "category", "difficulty")
        );
    }
}
