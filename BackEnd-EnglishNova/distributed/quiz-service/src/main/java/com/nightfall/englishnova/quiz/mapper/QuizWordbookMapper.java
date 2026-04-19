package com.nightfall.englishnova.quiz.mapper;

import com.nightfall.englishnova.quiz.domain.vo.WordbookProgressVo;
import com.nightfall.englishnova.quiz.domain.vo.WordbookSummaryVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QuizWordbookMapper {
    List<WordbookSummaryVo> listWordbooks(@Param("userId") long userId);
    int countOwnedWordbook(@Param("userId") long userId, @Param("wordbookId") long wordbookId);
    WordbookProgressVo loadProgress(@Param("userId") long userId, @Param("wordbookId") long wordbookId);
}
