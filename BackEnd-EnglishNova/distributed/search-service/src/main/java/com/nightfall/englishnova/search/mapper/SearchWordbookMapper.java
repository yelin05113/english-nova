package com.nightfall.englishnova.search.mapper;

import com.nightfall.englishnova.search.domain.po.PublicWordbookPo;
import com.nightfall.englishnova.search.domain.vo.WordbookCleanupVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SearchWordbookMapper {

    Long findPublicWordbookId(@Param("userId") long userId, @Param("sourceName") String sourceName);

    void insertPublicWordbook(PublicWordbookPo row);

    void syncWordbookCount(@Param("wordbookId") long wordbookId);

    List<WordbookCleanupVo> loadWordbookCleanupRows();

    void updateWordbookCleanup(@Param("id") long id, @Param("name") String name, @Param("sourceName") String sourceName);
}
