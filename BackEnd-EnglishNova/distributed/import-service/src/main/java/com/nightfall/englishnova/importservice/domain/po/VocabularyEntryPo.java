package com.nightfall.englishnova.importservice.domain.po;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VocabularyEntryPo {

    private long userId;
    private long wordbookId;
    private String word;
    private String phonetic;
    private String meaningCn;
    private String exampleSentence;
    private String category;
    private int difficulty;
    private String audioUrl;
    private String importSource;
}
