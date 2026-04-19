package com.nightfall.englishnova.search.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetailVo {
    private long entryId;
    private Long ownerUserId;
    private long wordbookId;
    private String wordbookName;
    private String word;
    private String phonetic;
    private String meaningCn;
    private String exampleSentence;
    private String category;
    private String definitionEn;
    private String tags;
    private Integer bncRank;
    private Integer frqRank;
    private Double wordfreqZipf;
    private String exchangeInfo;
    private String dataQuality;
    private int difficulty;
    private String visibility;
    private String audioUrl;
    private String importSource;
    private String sourceName;
}
