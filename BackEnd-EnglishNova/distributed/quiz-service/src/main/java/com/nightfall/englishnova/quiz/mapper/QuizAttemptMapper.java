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
    QuestionVo loadCurrentQuestion(@Param("sessionId") String sessionId);
    void updateOptions(
            @Param("attemptId") long attemptId,
            @Param("optionA") String optionA,
            @Param("optionAWord") String optionAWord,
            @Param("optionAMeaningCn") String optionAMeaningCn,
            @Param("optionB") String optionB,
            @Param("optionBWord") String optionBWord,
            @Param("optionBMeaningCn") String optionBMeaningCn,
            @Param("optionC") String optionC,
            @Param("optionCWord") String optionCWord,
            @Param("optionCMeaningCn") String optionCMeaningCn,
            @Param("optionD") String optionD,
            @Param("optionDWord") String optionDWord,
            @Param("optionDMeaningCn") String optionDMeaningCn,
            @Param("correctOption") String correctOption,
            @Param("promptText") String promptText
    );
    void recordWrongSubmission(@Param("attemptId") long attemptId);
    void markSelected(@Param("attemptId") long attemptId, @Param("selectedOption") String selectedOption, @Param("correct") boolean correct);
}
