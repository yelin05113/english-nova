package com.nightfall.englishnova.quiz.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionVo {
    private long id;
    private String promptType;
    private String promptText;
    private String currentWord;
    private String phonetic;
    private String audioUrl;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
}
