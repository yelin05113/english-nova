package com.nightfall.englishnova.study.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccuracyVo {

    private int answeredQuestions;
    private int correctAnswers;
}
