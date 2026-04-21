package com.nightfall.englishnova.search.controller;

import com.nightfall.englishnova.search.service.SearchCatalogService;
import com.nightfall.englishnova.shared.auth.CurrentUser;
import com.nightfall.englishnova.shared.auth.RequestUserExtractor;
import com.nightfall.englishnova.shared.common.ApiResponse;
import com.nightfall.englishnova.shared.dto.PublicCatalogImportJobDto;
import com.nightfall.englishnova.shared.dto.PublicCatalogImportJobRequest;
import com.nightfall.englishnova.shared.dto.PublicCatalogImportRequest;
import com.nightfall.englishnova.shared.dto.PublicCatalogImportResultDto;
import com.nightfall.englishnova.shared.dto.SearchSuggestionDto;
import com.nightfall.englishnova.shared.dto.WordDetailDto;
import com.nightfall.englishnova.shared.dto.WordSearchResponseDto;
import com.nightfall.englishnova.shared.enums.VocabularyEntryType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 搜索相关 HTTP 接口控制器。提供单词搜索、搜索建议、词条详情及公共词库导入等端点。
 */
@RestController
@RequestMapping({"/api/search", "/search"})
public class SearchController {

    private final SearchCatalogService searchCatalogService;

    /**
     * 构造搜索控制器。
     *
     * @param searchCatalogService 搜索目录服务
     */
    public SearchController(SearchCatalogService searchCatalogService) {
        this.searchCatalogService = searchCatalogService;
    }

    /**
     * 搜索单词。
     *
     * @param q       搜索关键词
     * @param request HTTP 请求
     * @return 搜索结果
     */
    @GetMapping("/words")
    public ApiResponse<WordSearchResponseDto> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Long wordbookId,
            HttpServletRequest request
    ) {
        CurrentUser user = RequestUserExtractor.optional(request);
        return ApiResponse.success(searchCatalogService.searchVocabulary(q, user, wordbookId));
    }

    /**
     * 获取搜索建议。
     *
     * @param q       搜索关键词
     * @param request HTTP 请求
     * @return 搜索建议列表
     */
    @GetMapping("/suggestions")
    public ApiResponse<List<SearchSuggestionDto>> suggestions(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Long wordbookId,
            HttpServletRequest request
    ) {
        CurrentUser user = RequestUserExtractor.optional(request);
        return ApiResponse.success(searchCatalogService.searchSuggestions(q, user, wordbookId));
    }

    /**
     * 获取单词详情。
     *
     * @param entryId 词条 ID
     * @param request HTTP 请求
     * @return 单词详情
     */
    @GetMapping("/words/{entryId}")
    public ApiResponse<WordDetailDto> wordDetail(
            @PathVariable long entryId,
            @RequestParam VocabularyEntryType entryType,
            HttpServletRequest request
    ) {
        CurrentUser user = RequestUserExtractor.optional(request);
        return ApiResponse.success(searchCatalogService.getWordDetail(entryId, entryType, user));
    }

    /**
     * 导入公共词库。
     *
     * @param request        导入请求
     * @param servletRequest HTTP 请求
     * @return 导入结果
     */
    @PostMapping("/public-catalog/import")
    public ApiResponse<PublicCatalogImportResultDto> importPublicCatalog(
            @RequestBody(required = false) PublicCatalogImportRequest request,
            HttpServletRequest servletRequest
    ) {
        RequestUserExtractor.require(servletRequest);
        return ApiResponse.success(searchCatalogService.importPublicCatalog(request));
    }

    @PostMapping("/public-catalog/import-high-frequency")
    public ApiResponse<PublicCatalogImportJobDto> importHighFrequencyPublicCatalog(
            @RequestBody(required = false) PublicCatalogImportJobRequest request,
            HttpServletRequest servletRequest
    ) {
        CurrentUser user = RequestUserExtractor.require(servletRequest);
        return ApiResponse.success(searchCatalogService.createHighFrequencyPublicCatalogJob(request, user));
    }

    @GetMapping("/public-catalog/import-jobs/{jobId}")
    public ApiResponse<PublicCatalogImportJobDto> publicCatalogImportJob(
            @PathVariable long jobId,
            HttpServletRequest servletRequest
    ) {
        RequestUserExtractor.require(servletRequest);
        return ApiResponse.success(searchCatalogService.getPublicCatalogImportJob(jobId));
    }

    @PostMapping("/public-catalog/import-jobs/{jobId}/retry-failed")
    public ApiResponse<PublicCatalogImportJobDto> retryPublicCatalogImportJob(
            @PathVariable long jobId,
            HttpServletRequest servletRequest
    ) {
        RequestUserExtractor.require(servletRequest);
        return ApiResponse.success(searchCatalogService.retryFailedPublicCatalogImportJob(jobId));
    }

    @PostMapping("/public-catalog/import-jobs/{jobId}/cancel")
    public ApiResponse<PublicCatalogImportJobDto> cancelPublicCatalogImportJob(
            @PathVariable long jobId,
            HttpServletRequest servletRequest
    ) {
        RequestUserExtractor.require(servletRequest);
        return ApiResponse.success(searchCatalogService.cancelPublicCatalogImportJob(jobId));
    }
}
