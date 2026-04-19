package com.nightfall.englishnova.importservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    int countById(@Param("userId") long userId);
}
