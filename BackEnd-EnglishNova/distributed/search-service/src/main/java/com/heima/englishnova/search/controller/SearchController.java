package com.heima.englishnova.search.controller;

import com.heima.englishnova.search.service.SearchCatalogService;
import com.heima.englishnova.shared.auth.CurrentUser;
import com.heima.englishnova.shared.auth.RequestUserExtractor;
import com.heima.englishnova.shared.common.ApiResponse;
import com.heima.englishnova.shared.dto.WordSearchResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
