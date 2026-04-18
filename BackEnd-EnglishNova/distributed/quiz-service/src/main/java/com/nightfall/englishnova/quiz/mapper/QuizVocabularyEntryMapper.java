package com.nightfall.englishnova.quiz.mapper;

import com.nightfall.englishnova.quiz.domain.vo.EntryVo;
import com.nightfall.englishnova.quiz.domain.vo.VocabularyEntryVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QuizVocabularyEntryMapper {
    List<VocabularyEntryVo> listEntries(@Param("userId") long userId, @Param("wordbookId") long wordbookId);
    List<EntryVo> loadWordbookEntries(@Param("userId") long userId, @Param("wordbookId") long wordbookId);
    List<String> loadUserWordDistractors(@Param("userId") long userId, @Param("entryId") long entryId);
    List<String> loadUserMeaningDistractors(@Param("userId") long userId, @Param("entryId") long entryId);
    List<String> loadPublicWordDistractors(@Param("entryId") long entryId);
    List<String> loadPublicMeaningDistractors(@Param("entryId") long entryId);
}
