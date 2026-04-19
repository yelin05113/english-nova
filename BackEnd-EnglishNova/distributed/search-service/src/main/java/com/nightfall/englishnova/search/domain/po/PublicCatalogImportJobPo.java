package com.nightfall.englishnova.search.domain.po;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicCatalogImportJobPo {
    private Long id;
    private String sourceName;
    private String status;
    private int totalWords;
    private boolean refreshExisting;
    private int batchSize;
    private Long createdByUserId;
}
