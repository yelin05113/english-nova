package com.nightfall.englishnova.search.domain.vo;

import lombok.Data;

@Data
public class PublicCatalogImportItemVo {
    private long id;
    private long jobId;
    private String word;
    private int attemptCount;
}
