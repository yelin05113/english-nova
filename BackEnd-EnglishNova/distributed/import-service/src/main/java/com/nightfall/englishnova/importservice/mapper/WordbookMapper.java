package com.nightfall.englishnova.importservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nightfall.englishnova.importservice.domain.po.WordbookPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WordbookMapper extends BaseMapper<WordbookPo> {

    void syncWordbookCount(@Param("wordbookId") long wordbookId);
}
