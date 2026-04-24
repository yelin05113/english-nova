package com.nightfall.englishnova.search.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicWordbookEntryRow {
    private long publicEntryId;
    private int sortOrder;
    private String word;
    private String phonetic;
    private String meaningCn;
    private String exampleSentence;
    private Integer bncRank;
    private Integer frqRank;
    private Double wordfreqZipf;
}
