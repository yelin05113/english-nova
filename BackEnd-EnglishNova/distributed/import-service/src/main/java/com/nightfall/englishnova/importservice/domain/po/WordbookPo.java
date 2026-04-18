package com.nightfall.englishnova.importservice.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("wordbooks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WordbookPo {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String platform;
    private String sourceName;
    private String importSource;
    private Integer wordCount;
}
