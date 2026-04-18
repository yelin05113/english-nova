package com.nightfall.englishnova.search.service;

import com.nightfall.englishnova.shared.auth.CurrentUser;
import com.nightfall.englishnova.shared.dto.PublicCatalogImportRequest;
import com.nightfall.englishnova.shared.dto.PublicCatalogImportResultDto;
import com.nightfall.englishnova.shared.dto.SearchSuggestionDto;
import com.nightfall.englishnova.shared.dto.WordDetailDto;
import com.nightfall.englishnova.shared.dto.WordSearchResponseDto;

import java.util.List;

public interface SearchCatalogService {

    WordSearchResponseDto searchVocabulary(String keyword, CurrentUser user);

    List<SearchSuggestionDto> searchSuggestions(String keyword, CurrentUser user);

    WordDetailDto getWordDetail(long entryId, CurrentUser user);

    PublicCatalogImportResultDto importPublicCatalog(PublicCatalogImportRequest request);
}
