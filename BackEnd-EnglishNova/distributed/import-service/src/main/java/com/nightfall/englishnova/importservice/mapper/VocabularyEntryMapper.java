package com.nightfall.englishnova.importservice.mapper;

import com.nightfall.englishnova.importservice.domain.po.VocabularyEntryPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VocabularyEntryMapper {

    void batchInsert(@Param("entries") List<VocabularyEntryPo> entries);

    List<Long> findIdsByUserAndWordbook(@Param("userId") long userId, @Param("wordbookId") long wordbookId);
}
