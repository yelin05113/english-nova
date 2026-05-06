package com.nightfall.englishnova.search.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExampleEnrichmentTaskVo {
    private long id;
    private String entryType;
    private long entryId;
    private String status;
    private int attemptCount;
    private String lastError;
}
