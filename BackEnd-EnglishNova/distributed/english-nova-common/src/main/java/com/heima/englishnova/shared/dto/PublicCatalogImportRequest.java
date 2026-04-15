package com.heima.englishnova.shared.dto;

import java.util.List;

/**
 * 公共词库导入请求。
 *
 * @param words            待导入的单词列表
 * @param refreshExisting  是否刷新已有词条
 */
public record PublicCatalogImportRequest(
        List<String> words,
        Boolean refreshExisting
) {
}
