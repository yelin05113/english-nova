package com.nightfall.englishnova.quiz.mapper;

import com.nightfall.englishnova.quiz.domain.vo.SessionVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;

@Mapper
public interface QuizSessionMapper {
    void cancelActiveSessions(
            @Param("userId") long userId,
            @Param("targetType") String targetType,
            @Param("targetId") long targetId
    );
    void insertSession(
            @Param("id") String id,
            @Param("userId") long userId,
            @Param("targetType") String targetType,
            @Param("targetId") long targetId,
            @Param("mode") String mode,
            @Param("startOffset") int startOffset,
            @Param("totalQuestions") int totalQuestions,
            @Param("startedAt") Timestamp startedAt
    );
    SessionVo findByUserAndId(@Param("userId") long userId, @Param("sessionId") String sessionId);
    void completeSession(@Param("sessionId") String sessionId);
    void markCorrectAnswer(@Param("sessionId") String sessionId);
}
