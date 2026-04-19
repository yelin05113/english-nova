package com.nightfall.englishnova.importservice.importer;

import com.nightfall.englishnova.shared.enums.WordImportPlatform;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 导入适配器调度器，按平台类型分发到具体的 WordImportAdapter 实现。
 */
@Component
public class WordImportDispatcher {

    private final Map<WordImportPlatform, WordImportAdapter> adapterMap = new EnumMap<>(WordImportPlatform.class);

    /**
     * 构造函数，自动注册所有适配器。
     *
     * @param adapters 所有已注册的导入适配器列表
     */
    public WordImportDispatcher(List<WordImportAdapter> adapters) {
        for (WordImportAdapter adapter : adapters) {
            adapterMap.put(adapter.platform(), adapter);
        }
    }

    /**
     * 获取所有已注册的导入适配器列表。
     *
     * @return 适配器列表
     */
    public List<WordImportAdapter> getAdapters() {
        return List.copyOf(adapterMap.values());
    }

    /**
     * 按平台类型获取对应的导入适配器。
     *
     * @param platform 平台类型
     * @return 对应的适配器，若不存在则返回 null
     */
    public WordImportAdapter getAdapter(WordImportPlatform platform) {
        return adapterMap.get(platform);
    }
}
