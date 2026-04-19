package com.nightfall.englishnova.quiz.domain.vo;

import java.sql.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WordbookSummaryVo {
    private long id;
    private String name;
    private String platform;
    private int wordCount;
    private int clearedCount;
    private int pendingCount;
    private Timestamp createdAt;
}
