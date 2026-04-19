package com.nightfall.englishnova.search.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchDocumentVo {
    private long entryId;
    private Long ownerUserId;
    private String visibility;
    private long wordbookId;
    private String word;
    private String phonetic;
    private String meaningCn;
    private String exampleSentence;
    private String category;
    private String importSource;
}
