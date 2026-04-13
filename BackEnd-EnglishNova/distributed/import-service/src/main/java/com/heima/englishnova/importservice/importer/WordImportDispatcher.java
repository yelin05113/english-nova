package com.heima.englishnova.importservice.importer;

import com.heima.englishnova.shared.enums.WordImportPlatform;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class WordImportDispatcher {

    private final Map<WordImportPlatform, WordImportAdapter> adapterMap = new EnumMap<>(WordImportPlatform.class);

    public WordImportDispatcher(List<WordImportAdapter> adapters) {
        for (WordImportAdapter adapter : adapters) {
            adapterMap.put(adapter.platform(), adapter);
        }
    }

    public List<WordImportAdapter> getAdapters() {
        return List.copyOf(adapterMap.values());
    }

    public WordImportAdapter getAdapter(WordImportPlatform platform) {
        return adapterMap.get(platform);
    }
}
