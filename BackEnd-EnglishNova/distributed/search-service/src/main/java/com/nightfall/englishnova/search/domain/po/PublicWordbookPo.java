package com.nightfall.englishnova.search.domain.po;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicWordbookPo {
    private Long id;
    private long userId;
    private String name;
    private String platform;
    private String sourceName;
    private String importSource;
}
