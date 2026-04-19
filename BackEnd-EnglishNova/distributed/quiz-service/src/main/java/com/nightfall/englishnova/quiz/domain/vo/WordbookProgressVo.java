package com.nightfall.englishnova.quiz.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WordbookProgressVo {
    private int wordCount;
    private int clearedCount;
    private int inProgressCount;
    private int pendingCount;
}
