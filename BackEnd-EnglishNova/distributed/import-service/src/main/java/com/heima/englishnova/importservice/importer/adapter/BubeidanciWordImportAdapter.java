package com.heima.englishnova.importservice.importer.adapter;

import com.heima.englishnova.importservice.importer.WordImportAdapter;
import com.heima.englishnova.shared.dto.ImportPresetDto;
import com.heima.englishnova.shared.enums.WordImportPlatform;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BubeidanciWordImportAdapter implements WordImportAdapter {

    @Override
    public WordImportPlatform platform() {
        return WordImportPlatform.BUBEIDANCI;
    }

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
