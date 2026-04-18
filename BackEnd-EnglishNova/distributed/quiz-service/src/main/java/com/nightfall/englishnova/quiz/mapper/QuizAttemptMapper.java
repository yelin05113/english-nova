package com.nightfall.englishnova.quiz.mapper;

import com.nightfall.englishnova.quiz.domain.po.AttemptPo;
import com.nightfall.englishnova.quiz.domain.vo.AttemptVo;
import com.nightfall.englishnova.quiz.domain.vo.QuestionVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface QuizAttemptMapper {
    void insertAttempt(AttemptPo row);
    AttemptVo findByUserSessionAndId(@Param("userId") long userId, @Param("sessionId") String sessionId, @Param("attemptId") long attemptId);
    QuestionVo loadNextQuestion(@Param("sessionId") String sessionId);
    void markSelected(@Param("attemptId") long attemptId, @Param("selectedOption") String selectedOption, @Param("correct") boolean correct);
}
