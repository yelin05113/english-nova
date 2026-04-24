package com.nightfall.englishnova.search.domain.vo;

import java.sql.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicWordbookRow {
    private long id;
    private String name;
    private String sourceName;
    private String sourceUrl;
    private String licenseName;
    private String licenseUrl;
    private String tag;
    private int wordCount;
    private boolean subscribed;
    private int completedCount;
    private int wrongCount;
    private int nextSortOrder;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
