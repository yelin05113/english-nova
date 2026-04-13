package com.heima.englishnova.importservice.importer.adapter;

import com.heima.englishnova.importservice.importer.WordImportAdapter;
import com.heima.englishnova.shared.dto.ImportPresetDto;
import com.heima.englishnova.shared.enums.WordImportPlatform;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BaicizhanWordImportAdapter implements WordImportAdapter {

    @Override
    public WordImportPlatform platform() {
        return WordImportPlatform.BAICIZHAN;
    }

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
