package com.nightfall.englishnova.search.mapper;

import com.nightfall.englishnova.search.domain.vo.PublicWordbookEntryRow;
import com.nightfall.englishnova.search.domain.vo.PublicWordbookRow;
import com.nightfall.englishnova.search.domain.vo.WordbookCleanupVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SearchWordbookMapper {

    int countOwnedWordbook(@Param("userId") long userId, @Param("wordbookId") long wordbookId);

    List<PublicWordbookRow> listPublicWordbooks(@Param("userId") long userId);

    PublicWordbookRow findPublicWordbook(@Param("publicWordbookId") long publicWordbookId);

    PublicWordbookRow findUserPublicWordbook(@Param("userId") long userId, @Param("publicWordbookId") long publicWordbookId);

    List<PublicWordbookEntryRow> listPublicWordbookEntries(@Param("publicWordbookId") long publicWordbookId);

    int countUserPublicWordbook(@Param("userId") long userId, @Param("publicWordbookId") long publicWordbookId);

    void insertUserPublicWordbook(@Param("userId") long userId, @Param("publicWordbookId") long publicWordbookId);

    int deleteUserPublicWordbook(@Param("userId") long userId, @Param("publicWordbookId") long publicWordbookId);

    int resetUserPublicWordbook(@Param("userId") long userId, @Param("publicWordbookId") long publicWordbookId);

    void deleteUserPublicWordbookWrongEntries(@Param("userId") long userId, @Param("publicWordbookId") long publicWordbookId);

    void cancelActivePublicQuizSessions(@Param("userId") long userId, @Param("publicWordbookId") long publicWordbookId);

    List<WordbookCleanupVo> loadWordbookCleanupRows();

    void updateWordbookCleanup(@Param("id") long id, @Param("name") String name, @Param("sourceName") String sourceName);
}
