package com.nightfall.englishnova.search.mapper;

import com.nightfall.englishnova.search.domain.po.PublicWordbookPo;
import com.nightfall.englishnova.search.domain.vo.WordbookCleanupVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SearchWordbookMapper {

    int countOwnedWordbook(@Param("userId") long userId, @Param("wordbookId") long wordbookId);

    Long findPublicWordbookId(@Param("userId") long userId, @Param("name") String name);

    void insertPublicWordbook(PublicWordbookPo row);

    void updatePublicWordbookMetadata(
            @Param("wordbookId") long wordbookId,
            @Param("platform") String platform,
            @Param("sourceName") String sourceName,
            @Param("importSource") String importSource
    );

    void syncWordbookCount(@Param("wordbookId") long wordbookId);

    List<WordbookCleanupVo> loadWordbookCleanupRows();

    void updateWordbookCleanup(@Param("id") long id, @Param("name") String name, @Param("sourceName") String sourceName);
}
