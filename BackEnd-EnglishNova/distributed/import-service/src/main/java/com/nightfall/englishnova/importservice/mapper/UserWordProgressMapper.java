package com.nightfall.englishnova.importservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserWordProgressMapper {

    void batchInsertNewProgress(@Param("userId") long userId, @Param("entryIds") List<Long> entryIds, @Param("status") String status);
}
