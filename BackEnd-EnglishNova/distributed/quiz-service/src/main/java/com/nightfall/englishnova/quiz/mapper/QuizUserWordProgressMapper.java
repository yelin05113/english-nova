package com.nightfall.englishnova.quiz.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface QuizUserWordProgressMapper {
    void updateAfterAnswer(@Param("userId") long userId, @Param("entryId") long entryId,
                           @Param("correctIncrement") int correctIncrement, @Param("wrongIncrement") int wrongIncrement,
                           @Param("status") String status);
}
