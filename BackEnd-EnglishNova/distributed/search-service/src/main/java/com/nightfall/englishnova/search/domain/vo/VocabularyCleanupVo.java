package com.nightfall.englishnova.search.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VocabularyCleanupVo {
    private long id;
    private String word;
    private String importSource;
    private String phonetic;
    private String meaningCn;
    private String exampleSentence;
    private String category;
}
