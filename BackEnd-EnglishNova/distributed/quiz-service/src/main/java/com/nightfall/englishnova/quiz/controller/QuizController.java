package com.nightfall.englishnova.quiz.controller;

import com.nightfall.englishnova.quiz.service.QuizService;
import com.nightfall.englishnova.shared.auth.CurrentUser;
import com.nightfall.englishnova.shared.auth.RequestUserExtractor;
import com.nightfall.englishnova.shared.common.ApiResponse;
import com.nightfall.englishnova.shared.dto.CreateQuizSessionRequest;
import com.nightfall.englishnova.shared.dto.QuizAnswerRequest;
import com.nightfall.englishnova.shared.dto.QuizAnswerResultDto;
import com.nightfall.englishnova.shared.dto.QuizSessionStateDto;
import com.nightfall.englishnova.shared.dto.VocabularyEntryDto;
import com.nightfall.englishnova.shared.dto.WordbookProgressDto;
import com.nightfall.englishnova.shared.dto.WordbookSummaryDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 斩词相关 HTTP 接口控制器。提供词书列表、词条浏览、斩词会话与作答等端点。
 */
@RestController
public class QuizController {

    private final QuizService quizService;

    /**
     * 构造函数。
     *
     * @param quizService 斩词业务服务
     */
    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    /**
     * 获取当前用户的词书列表。
     *
     * @param request HTTP 请求
     * @return 词书概要列表
     */
    @GetMapping("/api/wordbooks")
    public ApiResponse<List<WordbookSummaryDto>> wordbooks(HttpServletRequest request) {
        CurrentUser user = RequestUserExtractor.require(request);
        return ApiResponse.success(quizService.listWordbooks(user));
    }

    /**
     * 获取指定词书的词条列表。
     *
     * @param wordbookId 词书 ID
     * @param request    HTTP 请求
     * @return 词条列表
     */
    @GetMapping("/api/wordbooks/{wordbookId}/entries")
    public ApiResponse<List<VocabularyEntryDto>> entries(@PathVariable long wordbookId, HttpServletRequest request) {
        CurrentUser user = RequestUserExtractor.require(request);
        return ApiResponse.success(quizService.listEntries(user, wordbookId));
    }

    /**
     * 获取指定词书的学习进度。
     *
     * @param wordbookId 词书 ID
     * @param request    HTTP 请求
     * @return 词书学习进度
     */
    @GetMapping("/api/wordbooks/{wordbookId}/progress")
    public ApiResponse<WordbookProgressDto> progress(@PathVariable long wordbookId, HttpServletRequest request) {
        CurrentUser user = RequestUserExtractor.require(request);
        return ApiResponse.success(quizService.getWordbookProgress(user, wordbookId));
    }

    /**
     * 创建斩词会话。
     *
     * @param request        创建会话请求
     * @param servletRequest HTTP 请求
     * @return 会话状态
     */
    @PostMapping("/api/quiz/sessions")
    public ApiResponse<QuizSessionStateDto> createSession(
            @Valid @RequestBody CreateQuizSessionRequest request,
            HttpServletRequest servletRequest
    ) {
        CurrentUser user = RequestUserExtractor.require(servletRequest);
        return ApiResponse.success(quizService.createSession(user, request));
    }

    /**
     * 获取斩词会话详情。
     *
     * @param sessionId 会话 ID
     * @param request   HTTP 请求
     * @return 会话状态
     */
    @GetMapping("/api/quiz/sessions/{sessionId}")
    public ApiResponse<QuizSessionStateDto> session(@PathVariable String sessionId, HttpServletRequest request) {
        CurrentUser user = RequestUserExtractor.require(request);
        return ApiResponse.success(quizService.getSessionState(user, sessionId));
    }

    /**
     * 提交斩词作答。
     *
     * @param sessionId      会话 ID
     * @param request        作答请求
     * @param servletRequest HTTP 请求
     * @return 作答结果
     */
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
