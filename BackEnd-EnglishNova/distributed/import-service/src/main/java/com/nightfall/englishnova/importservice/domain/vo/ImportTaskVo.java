package com.nightfall.englishnova.importservice.domain.vo;

import java.sql.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportTaskVo {

    private String taskId;
    private Long wordbookId;
    private String platform;
    private String sourceName;
    private int estimatedCards;
    private int importedCards;
    private String status;
    private Timestamp queuedAt;
    private Timestamp finishedAt;
    private String queueName;
}
