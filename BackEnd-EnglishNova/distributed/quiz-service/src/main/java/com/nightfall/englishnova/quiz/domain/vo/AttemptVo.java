package com.nightfall.englishnova.quiz.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttemptVo {
    private long id;
    private Long userVocabularyEntryId;
    private Long publicEntryId;
    private String correctOption;
    private String selectedOption;
    private int wrongSubmissions;
}
