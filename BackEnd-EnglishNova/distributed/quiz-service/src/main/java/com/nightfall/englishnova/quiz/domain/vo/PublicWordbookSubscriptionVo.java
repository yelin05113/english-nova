package com.nightfall.englishnova.quiz.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicWordbookSubscriptionVo {
    private long publicWordbookId;
    private int currentSortOrder;
    private int completedCount;
    private int wrongCount;
    private int dailyTargetCount;
    private int todayCompletedCount;
    private int wordCount;
}
