package com.nightfall.englishnova.quiz.mapper;

import com.nightfall.englishnova.quiz.domain.vo.EntryVo;
import com.nightfall.englishnova.quiz.domain.vo.VocabularyEntryVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QuizVocabularyEntryMapper {
    List<VocabularyEntryVo> listEntries(@Param("userId") long userId, @Param("wordbookId") long wordbookId);
    EntryVo loadUserEntryById(@Param("userId") long userId, @Param("entryId") long entryId);
    EntryVo loadPublicVocabularyEntryById(@Param("entryId") long entryId);
    EntryVo loadPublicEntryById(@Param("publicWordbookId") long publicWordbookId, @Param("entryId") long entryId);
    EntryVo loadWordNotebookEntryById(@Param("userId") long userId, @Param("entryId") long entryId);
    EntryVo loadWordbookEntryByOffset(@Param("userId") long userId, @Param("wordbookId") long wordbookId, @Param("offset") int offset);
    EntryVo loadPublicWordbookEntryBySortOrder(@Param("publicWordbookId") long publicWordbookId, @Param("sortOrder") int sortOrder);
    EntryVo loadWordNotebookEntryByOffset(@Param("userId") long userId, @Param("notebookId") long notebookId, @Param("offset") int offset);
    List<String> loadUserWordDistractors(@Param("userId") long userId, @Param("entryId") long entryId);
    List<String> loadUserMeaningDistractors(
            @Param("userId") long userId,
            @Param("wordbookId") long wordbookId,
            @Param("entryId") long entryId
    );
    List<String> loadPublicMeaningDistractors(
            @Param("publicWordbookId") long publicWordbookId,
            @Param("entryId") long entryId
    );
    List<EntryVo> loadUserDistractorCandidates(
            @Param("userId") long userId,
            @Param("wordbookId") long wordbookId,
            @Param("entryId") long entryId
    );
    List<EntryVo> loadPublicDistractorCandidates(
            @Param("publicWordbookId") long publicWordbookId,
            @Param("entryId") long entryId
    );
    List<EntryVo> loadWordNotebookDistractorCandidates(
            @Param("userId") long userId,
            @Param("notebookId") long notebookId,
            @Param("entryId") long entryId
    );
    List<EntryVo> loadNotebookFallbackDistractorCandidates(@Param("word") String word);
    List<EntryVo> loadPublicRandomDistractorEntries(
            @Param("publicWordbookId") long publicWordbookId,
            @Param("entryId") long entryId
    );
    List<EntryVo> loadWordNotebookRandomDistractorEntries(
            @Param("userId") long userId,
            @Param("notebookId") long notebookId,
            @Param("entryId") long entryId
    );
    List<EntryVo> loadNotebookFallbackRandomDistractorEntries(@Param("word") String word);
    List<EntryVo> loadPublicWordbookOptionEntries(@Param("publicWordbookId") long publicWordbookId);
}
