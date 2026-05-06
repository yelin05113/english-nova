package com.nightfall.englishnova.quiz.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionVo {
    private long id;
    private Long userVocabularyEntryId;
    private Long wordNotebookEntryId;
    private Long publicEntryId;
    private String promptType;
    private String promptText;
    private String currentWord;
    private String phonetic;
    private String audioUrl;
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
}
