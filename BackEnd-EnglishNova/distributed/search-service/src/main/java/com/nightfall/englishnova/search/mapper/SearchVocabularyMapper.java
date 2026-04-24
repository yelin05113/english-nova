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

    void updatePublicAudioUrl(@Param("entryId") long entryId, @Param("audioUrl") String audioUrl);

    List<SearchDocumentVo> listUserByWordbook(@Param("userId") long userId, @Param("wordbookId") long wordbookId);

    Long findExistingPublicEntryId(@Param("word") String word);

    void insertPublicEntry(PublicEntryPo row);

    void updatePublicEntry(
            @Param("entryId") long entryId,
            @Param("word") String word,
            @Param("phonetic") String phonetic,
            @Param("meaningCn") String meaningCn,
            @Param("exampleSentence") String exampleSentence,
            @Param("bncRank") Integer bncRank,
            @Param("frqRank") Integer frqRank,
            @Param("wordfreqZipf") Double wordfreqZipf,
            @Param("exchangeInfo") String exchangeInfo,
            @Param("dataQuality") String dataQuality,
            @Param("audioUrl") String audioUrl,
            @Param("importSource") String importSource
    );

    SearchDocumentVo findPublicDocumentById(@Param("entryId") long entryId);

    SearchDocumentVo findUserDocumentById(@Param("entryId") long entryId);

    Long findPublicEntryId(@Param("word") String word);

    DetailVo loadPublicDetailRow(@Param("entryId") long entryId);

    DetailVo loadUserDetailRow(@Param("entryId") long entryId);

    List<SearchDocumentVo> loadAllPublicRows();

    List<SearchDocumentVo> loadAllUserRows();

    List<VocabularyCleanupVo> loadPublicVocabularyCleanupRows();

    List<VocabularyCleanupVo> loadUserVocabularyCleanupRows();

    void updatePublicVocabularyCleanup(
            @Param("id") long id,
            @Param("phonetic") String phonetic,
            @Param("meaningCn") String meaningCn,
            @Param("exampleSentence") String exampleSentence
    );

    void updateUserVocabularyCleanup(
            @Param("id") long id,
            @Param("phonetic") String phonetic,
            @Param("meaningCn") String meaningCn,
            @Param("exampleSentence") String exampleSentence,
            @Param("category") String category
    );
}
