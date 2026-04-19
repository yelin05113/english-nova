package com.nightfall.englishnova.quiz.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttemptVo {
    private long id;
    private long vocabularyEntryId;
    private String correctOption;
    private String selectedOption;
}
