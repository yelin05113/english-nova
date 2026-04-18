package com.nightfall.englishnova.importservice.importer.adapter;

import com.nightfall.englishnova.importservice.importer.WordImportAdapter;
import com.nightfall.englishnova.shared.dto.ImportPresetDto;
import com.nightfall.englishnova.shared.enums.WordImportPlatform;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 百词斩（Baicizhan）导入适配器，声明支持的平台与文件格式预设。
 */
@Component
public class BaicizhanWordImportAdapter implements WordImportAdapter {

    /**
     * 返回适配器对应的导入平台。
     *
     * @return {@link WordImportPlatform#BAICIZHAN}
     */
    @Override
    public WordImportPlatform platform() {
        return WordImportPlatform.BAICIZHAN;
    }

    /**
     * 返回 Baicizhan 导入预设信息。
     *
     * @return 导入预设 DTO
     */
    @Override
    public ImportPresetDto preset() {
        return new ImportPresetDto(
                platform(),
                "Baicizhan Workbook",
                "Import workbook exports and preserve phonetics, meanings, and tags.",
                List.of("csv", "xlsx", "json"),
                List.of("word", "phonetic", "meaning", "tags", "image")
        );
    }
}
