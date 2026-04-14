package com.heima.englishnova.search.controller;

import com.heima.englishnova.search.service.SearchCatalogService;
import com.heima.englishnova.shared.auth.CurrentUser;
import com.heima.englishnova.shared.auth.RequestUserExtractor;
import com.heima.englishnova.shared.common.ApiResponse;
import com.heima.englishnova.shared.dto.PublicCatalogImportRequest;
import com.heima.englishnova.shared.dto.PublicCatalogImportResultDto;
import com.heima.englishnova.shared.dto.SearchSuggestionDto;
import com.heima.englishnova.shared.dto.WordDetailDto;
import com.heima.englishnova.shared.dto.WordSearchResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchCatalogService searchCatalogService;

    public SearchController(SearchCatalogService searchCatalogService) {
        this.searchCatalogService = searchCatalogService;
    }

    @GetMapping("/words")
    public ApiResponse<WordSearchResponseDto> search(
            @RequestParam(defaultValue = "") String q,
            HttpServletRequest request
    ) {
        CurrentUser user = RequestUserExtractor.optional(request);
        return ApiResponse.success(searchCatalogService.searchVocabulary(q, user));
    }

    @GetMapping("/suggestions")
    public ApiResponse<List<SearchSuggestionDto>> suggestions(
            @RequestParam(defaultValue = "") String q,
            HttpServletRequest request
    ) {
        CurrentUser user = RequestUserExtractor.optional(request);
        return ApiResponse.success(searchCatalogService.searchSuggestions(q, user));
    }

    @GetMapping("/words/{entryId}")
    public ApiResponse<WordDetailDto> wordDetail(
            @PathVariable long entryId,
            HttpServletRequest request
    ) {
        CurrentUser user = RequestUserExtractor.optional(request);
        return ApiResponse.success(searchCatalogService.getWordDetail(entryId, user));
    }

    @PostMapping("/public-catalog/import")
    public ApiResponse<PublicCatalogImportResultDto> importPublicCatalog(
            @RequestBody(required = false) PublicCatalogImportRequest request,
            HttpServletRequest servletRequest
    ) {
        RequestUserExtractor.require(servletRequest);
        return ApiResponse.success(searchCatalogService.importPublicCatalog(request));
    }
}
