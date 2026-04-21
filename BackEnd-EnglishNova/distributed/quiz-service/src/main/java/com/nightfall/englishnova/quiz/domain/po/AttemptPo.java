package com.nightfall.englishnova.quiz.domain.po;

import java.sql.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttemptPo {
    private String sessionId;
    private long userId;
    private Long userVocabularyEntryId;
    private Long publicEntryId;
    private String promptType;
    private String promptText;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String correctOption;
    private int wrongSubmissions;
    private Timestamp createdAt;
}
