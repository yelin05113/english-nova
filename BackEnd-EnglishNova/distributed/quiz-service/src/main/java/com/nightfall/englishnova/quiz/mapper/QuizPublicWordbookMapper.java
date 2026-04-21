package com.nightfall.englishnova.quiz.mapper;

import com.nightfall.englishnova.quiz.domain.vo.PublicWordbookSubscriptionVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface QuizPublicWordbookMapper {

    PublicWordbookSubscriptionVo findSubscription(@Param("userId") long userId, @Param("publicWordbookId") long publicWordbookId);

    int insertWrongEntry(@Param("userId") long userId, @Param("publicWordbookId") long publicWordbookId, @Param("publicEntryId") long publicEntryId);

    void incrementWrongCount(@Param("userId") long userId, @Param("publicWordbookId") long publicWordbookId);

    void advanceAfterCorrect(@Param("userId") long userId, @Param("publicWordbookId") long publicWordbookId);
}
