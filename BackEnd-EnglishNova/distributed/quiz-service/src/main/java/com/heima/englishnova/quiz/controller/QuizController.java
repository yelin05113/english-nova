package com.heima.englishnova.quiz.controller;

import com.heima.englishnova.quiz.service.QuizService;
import com.heima.englishnova.shared.auth.CurrentUser;
import com.heima.englishnova.shared.auth.RequestUserExtractor;
import com.heima.englishnova.shared.common.ApiResponse;
import com.heima.englishnova.shared.dto.CreateQuizSessionRequest;
import com.heima.englishnova.shared.dto.QuizAnswerRequest;
import com.heima.englishnova.shared.dto.QuizAnswerResultDto;
import com.heima.englishnova.shared.dto.QuizSessionStateDto;
import com.heima.englishnova.shared.dto.VocabularyEntryDto;
import com.heima.englishnova.shared.dto.WordbookProgressDto;
import com.heima.englishnova.shared.dto.WordbookSummaryDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @GetMapping("/api/wordbooks")
    public ApiResponse<List<WordbookSummaryDto>> wordbooks(HttpServletRequest request) {
        CurrentUser user = RequestUserExtractor.require(request);
        return ApiResponse.success(quizService.listWordbooks(user));
    }

    @GetMapping("/api/wordbooks/{wordbookId}/entries")
    public ApiResponse<List<VocabularyEntryDto>> entries(@PathVariable long wordbookId, HttpServletRequest request) {
        CurrentUser user = RequestUserExtractor.require(request);
        return ApiResponse.success(quizService.listEntries(user, wordbookId));
    }

    @GetMapping("/api/wordbooks/{wordbookId}/progress")
    public ApiResponse<WordbookProgressDto> progress(@PathVariable long wordbookId, HttpServletRequest request) {
        CurrentUser user = RequestUserExtractor.require(request);
        return ApiResponse.success(quizService.getWordbookProgress(user, wordbookId));
    }

    @PostMapping("/api/quiz/sessions")
    public ApiResponse<QuizSessionStateDto> createSession(
            @Valid @RequestBody CreateQuizSessionRequest request,
            HttpServletRequest servletRequest
    ) {
        CurrentUser user = RequestUserExtractor.require(servletRequest);
        return ApiResponse.success(quizService.createSession(user, request));
    }

    @GetMapping("/api/quiz/sessions/{sessionId}")
    public ApiResponse<QuizSessionStateDto> session(@PathVariable String sessionId, HttpServletRequest request) {
        CurrentUser user = RequestUserExtractor.require(request);
        return ApiResponse.success(quizService.getSessionState(user, sessionId));
    }

    @PostMapping("/api/quiz/sessions/{sessionId}/answers")
    public ApiResponse<QuizAnswerResultDto> answer(
            @PathVariable String sessionId,
            @Valid @RequestBody QuizAnswerRequest request,
            HttpServletRequest servletRequest
    ) {
        CurrentUser user = RequestUserExtractor.require(servletRequest);
        return ApiResponse.success(quizService.answer(user, sessionId, request));
    }
}
