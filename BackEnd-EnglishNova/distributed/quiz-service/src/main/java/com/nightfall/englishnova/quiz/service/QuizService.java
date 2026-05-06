package com.nightfall.englishnova.quiz.service;

import com.nightfall.englishnova.shared.auth.CurrentUser;
import com.nightfall.englishnova.shared.dto.AddWordNotebookEntryRequest;
import com.nightfall.englishnova.shared.dto.AddWordNotebookEntryResultDto;
import com.nightfall.englishnova.shared.dto.CreateQuizSessionRequest;
import com.nightfall.englishnova.shared.dto.CreateWordNotebookRequest;
import com.nightfall.englishnova.shared.dto.QuizAnswerRequest;
import com.nightfall.englishnova.shared.dto.QuizAnswerResultDto;
import com.nightfall.englishnova.shared.dto.QuizSessionStateDto;
import com.nightfall.englishnova.shared.dto.VocabularyEntryDto;
import com.nightfall.englishnova.shared.dto.WordNotebookEntryDto;
import com.nightfall.englishnova.shared.dto.WordNotebookSummaryDto;
import com.nightfall.englishnova.shared.dto.WordbookProgressDto;
import com.nightfall.englishnova.shared.dto.WordbookSummaryDto;

import java.util.List;

public interface QuizService {

    List<WordbookSummaryDto> listWordbooks(CurrentUser user);

    List<VocabularyEntryDto> listEntries(CurrentUser user, long wordbookId);

    WordbookProgressDto getWordbookProgress(CurrentUser user, long wordbookId);

    List<WordNotebookSummaryDto> listWordNotebooks(CurrentUser user, String word);

    WordNotebookSummaryDto createWordNotebook(CurrentUser user, CreateWordNotebookRequest request);

    List<WordNotebookEntryDto> listWordNotebookEntries(CurrentUser user, long notebookId);

    AddWordNotebookEntryResultDto addWordNotebookEntry(CurrentUser user, long notebookId, AddWordNotebookEntryRequest request);

    int removeWordNotebookEntries(CurrentUser user, String word);

    QuizSessionStateDto createSession(CurrentUser user, CreateQuizSessionRequest request);

    QuizSessionStateDto getSessionState(CurrentUser user, String sessionId);

    QuizSessionStateDto refreshQuestionOptions(CurrentUser user, String sessionId, long attemptId);

    QuizAnswerResultDto answer(CurrentUser user, String sessionId, QuizAnswerRequest request);
}
