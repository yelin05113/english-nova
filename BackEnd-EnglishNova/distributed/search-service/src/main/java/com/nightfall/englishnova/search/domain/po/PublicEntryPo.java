package com.nightfall.englishnova.search.domain.po;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicEntryPo {
    private Long id;
    private long userId;
    private long wordbookId;
    private String word;
    private String phonetic;
    private String meaningCn;
    private String exampleSentence;
    private String category;
    private int difficulty;
    private String visibility;
    private String audioUrl;
    private String importSource;
}
