package com.heima.englishnova.importservice.importer.adapter;

import com.heima.englishnova.importservice.importer.WordImportAdapter;
import com.heima.englishnova.shared.dto.ImportPresetDto;
import com.heima.englishnova.shared.enums.WordImportPlatform;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ShanbayWordImportAdapter implements WordImportAdapter {

    @Override
    public WordImportPlatform platform() {
        return WordImportPlatform.SHANBAY;
    }

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
