package com.nightfall.englishnova.quiz.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VocabularyEntryVo {
    private long id;
    private String word;
    private String phonetic;
    private String meaningCn;
    private String exampleSentence;
    private String category;
    private int difficulty;
    private String visibility;
}
