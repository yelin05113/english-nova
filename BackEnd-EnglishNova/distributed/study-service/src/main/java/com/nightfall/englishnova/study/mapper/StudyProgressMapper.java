package com.nightfall.englishnova.study.mapper;

import com.nightfall.englishnova.study.domain.vo.AccuracyVo;
import com.nightfall.englishnova.study.domain.vo.ProgressVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StudyProgressMapper {

    ProgressVo loadProgress(@Param("userId") long userId);

    AccuracyVo loadAccuracy(@Param("userId") long userId);
}
