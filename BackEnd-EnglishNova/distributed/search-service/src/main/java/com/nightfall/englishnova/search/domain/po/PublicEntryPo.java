package com.nightfall.englishnova.search.domain.po;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicEntryPo {
    private Long id;
    private String word;
    private String phonetic;
    private String meaningCn;
    private String exampleSentence;
    private Integer bncRank;
    private Integer frqRank;
    private Double wordfreqZipf;
    private String exchangeInfo;
    private String dataQuality;
    private String audioUrl;
    private String importSource;
}
