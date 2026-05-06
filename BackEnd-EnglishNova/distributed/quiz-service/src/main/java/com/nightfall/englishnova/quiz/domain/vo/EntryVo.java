package com.nightfall.englishnova.quiz.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EntryVo {
    private long id;
    private String word;
    private String phonetic;
    private String meaningCn;
    private String category;
    private String audioUrl;
    private String exampleSentence;
    private String correctedExampleSentence;
    private String chineseSentence;
    private String exampleAudioUrl;
    private String optionA;
    private String optionAWord;
    private String optionAMeaningCn;
    private String optionB;
    private String optionBWord;
    private String optionBMeaningCn;
    private String optionC;
    private String optionCWord;
    private String optionCMeaningCn;
    private String optionD;
    private String optionDWord;
    private String optionDMeaningCn;
    private String correctOption;
    private String sourceEntryType;
    private Long sourceEntryId;
}
