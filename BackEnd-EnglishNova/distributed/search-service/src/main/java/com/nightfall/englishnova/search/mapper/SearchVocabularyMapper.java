package com.nightfall.englishnova.search.mapper;

import com.nightfall.englishnova.search.domain.po.PublicEntryPo;
import com.nightfall.englishnova.search.domain.vo.DetailVo;
import com.nightfall.englishnova.search.domain.vo.SearchDocumentVo;
import com.nightfall.englishnova.search.domain.vo.VocabularyCleanupVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SearchVocabularyMapper {

    void updateAudioUrl(@Param("entryId") long entryId, @Param("audioUrl") String audioUrl);

    List<SearchDocumentVo> listByUserAndWordbook(@Param("userId") long userId, @Param("wordbookId") long wordbookId);

    Long findExistingPublicEntryId(@Param("userId") long userId, @Param("visibility") String visibility, @Param("word") String word);

    void insertPublicEntry(PublicEntryPo row);

    void updatePublicEntry(
            @Param("entryId") long entryId,
            @Param("word") String word,
            @Param("phonetic") String phonetic,
            @Param("meaningCn") String meaningCn,
            @Param("exampleSentence") String exampleSentence,
            @Param("category") String category,
            @Param("difficulty") int difficulty,
            @Param("audioUrl") String audioUrl,
            @Param("importSource") String importSource
    );

    SearchDocumentVo findDocumentById(@Param("entryId") long entryId);

    Long findPublicEntryId(@Param("userId") long userId, @Param("wordbookId") long wordbookId, @Param("word") String word);

    DetailVo loadDetailRow(@Param("entryId") long entryId);

    List<SearchDocumentVo> loadAllRows();

    List<VocabularyCleanupVo> loadVocabularyCleanupRows();

    void updateVocabularyCleanup(
            @Param("id") long id,
            @Param("meaningCn") String meaningCn,
            @Param("exampleSentence") String exampleSentence,
            @Param("category") String category
    );
}
