package com.nightfall.englishnova.search.controller;

import com.nightfall.englishnova.search.service.AudioProxyPayload;
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
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping({"/api/search", "/search"})
public class SearchController {

    private final SearchCatalogService searchCatalogService;

    public SearchController(SearchCatalogService searchCatalogService) {
        this.searchCatalogService = searchCatalogService;
    }

    @GetMapping("/words")
    public ApiResponse<WordSearchResponseDto> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Long wordbookId,
            HttpServletRequest request
    ) {
        CurrentUser user = RequestUserExtractor.optional(request);
        return ApiResponse.success(searchCatalogService.searchVocabulary(q, user, wordbookId));
    }

    @GetMapping("/suggestions")
    public ApiResponse<List<SearchSuggestionDto>> suggestions(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Long wordbookId,
            HttpServletRequest request
    ) {
        CurrentUser user = RequestUserExtractor.optional(request);
        return ApiResponse.success(searchCatalogService.searchSuggestions(q, user, wordbookId));
    }

    @GetMapping("/words/{entryId}")
    public ApiResponse<WordDetailDto> wordDetail(
            @PathVariable long entryId,
            @RequestParam VocabularyEntryType entryType,
            HttpServletRequest request
    ) {
        CurrentUser user = RequestUserExtractor.optional(request);
        return ApiResponse.success(searchCatalogService.getWordDetail(entryId, entryType, user));
    }

    @GetMapping("/audio-proxy")
    public ResponseEntity<byte[]> audioProxy(@RequestParam String src) {
        AudioProxyPayload payload = searchCatalogService.getAudioProxy(src);
        MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
        if (payload.contentType() != null && !payload.contentType().isBlank()) {
            contentType = MediaType.parseMediaType(payload.contentType());
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .contentType(contentType)
                .body(payload.content());
    }

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
