package com.nightfall.englishnova.quiz.mapper;

import com.nightfall.englishnova.quiz.domain.po.WordNotebookEntryPo;
import com.nightfall.englishnova.quiz.domain.po.WordNotebookPo;
import com.nightfall.englishnova.quiz.domain.vo.WordNotebookEntryVo;
import com.nightfall.englishnova.quiz.domain.vo.WordNotebookSummaryVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QuizWordNotebookMapper {
    List<WordNotebookSummaryVo> listWordNotebooks(@Param("userId") long userId, @Param("normalizedWord") String normalizedWord);

    WordNotebookSummaryVo loadWordNotebookSummary(
            @Param("userId") long userId,
            @Param("notebookId") long notebookId,
            @Param("normalizedWord") String normalizedWord
    );

    int countOwnedNotebook(@Param("userId") long userId, @Param("notebookId") long notebookId);

    void insertWordNotebook(WordNotebookPo row);

    void touchWordNotebook(@Param("userId") long userId, @Param("notebookId") long notebookId);

    List<WordNotebookEntryVo> listWordNotebookEntries(@Param("userId") long userId, @Param("notebookId") long notebookId);

    int upsertWordNotebookEntry(WordNotebookEntryPo row);

    WordNotebookEntryVo loadWordNotebookEntry(@Param("userId") long userId, @Param("entryId") long entryId);

    List<Long> listWordNotebookIdsByNormalizedWord(@Param("userId") long userId, @Param("normalizedWord") String normalizedWord);

    int deleteWordNotebookEntriesByNormalizedWord(@Param("userId") long userId, @Param("normalizedWord") String normalizedWord);
}
