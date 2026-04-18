package com.nightfall.englishnova.quiz.service;

import com.nightfall.englishnova.shared.auth.CurrentUser;
import com.nightfall.englishnova.shared.dto.CreateQuizSessionRequest;
import com.nightfall.englishnova.shared.dto.QuizAnswerRequest;
import com.nightfall.englishnova.shared.dto.QuizAnswerResultDto;
import com.nightfall.englishnova.shared.dto.QuizSessionStateDto;
import com.nightfall.englishnova.shared.dto.VocabularyEntryDto;
import com.nightfall.englishnova.shared.dto.WordbookProgressDto;
import com.nightfall.englishnova.shared.dto.WordbookSummaryDto;

import java.util.List;

public interface QuizService {

    List<WordbookSummaryDto> listWordbooks(CurrentUser user);

    List<VocabularyEntryDto> listEntries(CurrentUser user, long wordbookId);

    WordbookProgressDto getWordbookProgress(CurrentUser user, long wordbookId);

    QuizSessionStateDto createSession(CurrentUser user, CreateQuizSessionRequest request);

    QuizSessionStateDto getSessionState(CurrentUser user, String sessionId);

    QuizAnswerResultDto answer(CurrentUser user, String sessionId, QuizAnswerRequest request);
}
