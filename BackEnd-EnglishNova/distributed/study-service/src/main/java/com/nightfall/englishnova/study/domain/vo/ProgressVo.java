package com.nightfall.englishnova.study.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProgressVo {

    private int totalWords;
    private int clearedWords;
    private int inProgressWords;
    private int newWords;
    private int wordbooks;
}
