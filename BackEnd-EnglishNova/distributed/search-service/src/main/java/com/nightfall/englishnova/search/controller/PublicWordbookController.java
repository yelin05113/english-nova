package com.nightfall.englishnova.search.controller;

import com.nightfall.englishnova.search.service.SearchCatalogService;
import com.nightfall.englishnova.shared.auth.CurrentUser;
import com.nightfall.englishnova.shared.auth.RequestUserExtractor;
import com.nightfall.englishnova.shared.common.ApiResponse;
import com.nightfall.englishnova.shared.dto.PublicWordbookDto;
import com.nightfall.englishnova.shared.dto.PublicWordbookEntryDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/public-wordbooks", "/api/search/public-wordbooks", "/public-wordbooks", "/search/public-wordbooks"})
public class PublicWordbookController {

    private final SearchCatalogService searchCatalogService;

    public PublicWordbookController(SearchCatalogService searchCatalogService) {
        this.searchCatalogService = searchCatalogService;
    }

    @GetMapping
    public ApiResponse<List<PublicWordbookDto>> listPublicWordbooks(HttpServletRequest request) {
        CurrentUser user = RequestUserExtractor.require(request);
        return ApiResponse.success(searchCatalogService.listPublicWordbooks(user));
    }

    @GetMapping("/{id}/entries")
    public ApiResponse<List<PublicWordbookEntryDto>> listEntries(
            @PathVariable long id,
            HttpServletRequest request
    ) {
        RequestUserExtractor.require(request);
        return ApiResponse.success(searchCatalogService.listPublicWordbookEntries(id));
    }

    @PostMapping("/{id}/subscribe")
    public ApiResponse<PublicWordbookDto> subscribe(
            @PathVariable long id,
            HttpServletRequest request
    ) {
        CurrentUser user = RequestUserExtractor.require(request);
        return ApiResponse.success(searchCatalogService.subscribePublicWordbook(id, user));
    }

    @PostMapping("/{id}/reset-progress")
    public ApiResponse<PublicWordbookDto> resetProgress(
            @PathVariable long id,
            HttpServletRequest request
    ) {
        CurrentUser user = RequestUserExtractor.require(request);
        return ApiResponse.success(searchCatalogService.resetPublicWordbookProgress(id, user));
    }
}
