package com.nightfall.englishnova.quiz.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionVo {
    private String id;
    private long userId;
    private long wordbookId;
    private String mode;
    private int totalQuestions;
    private int answeredQuestions;
    private int correctAnswers;
    private String status;
}
