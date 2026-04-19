package com.nightfall.englishnova.search.domain.vo;

import lombok.Data;

import java.time.Instant;

@Data
public class PublicCatalogImportJobVo {
    private long id;
    private String sourceName;
    private String status;
    private int totalWords;
    private int processedWords;
    private int importedWords;
    private int updatedWords;
    private int skippedWords;
    private int failedWords;
    private boolean refreshExisting;
    private int batchSize;
    private Instant startedAt;
    private Instant finishedAt;
    private Long createdByUserId;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
}
