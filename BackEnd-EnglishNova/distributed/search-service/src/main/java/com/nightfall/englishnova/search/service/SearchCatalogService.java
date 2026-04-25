package com.nightfall.englishnova.search.service;

import com.nightfall.englishnova.shared.auth.CurrentUser;
import com.nightfall.englishnova.shared.dto.PublicCatalogImportJobDto;
import com.nightfall.englishnova.shared.dto.PublicCatalogImportJobRequest;
import com.nightfall.englishnova.shared.dto.PublicCatalogImportRequest;
import com.nightfall.englishnova.shared.dto.PublicCatalogImportResultDto;
import com.nightfall.englishnova.shared.dto.PublicWordbookDto;
import com.nightfall.englishnova.shared.dto.PublicWordbookEntryDto;
import com.nightfall.englishnova.shared.dto.SearchSuggestionDto;
import com.nightfall.englishnova.shared.dto.WordDetailDto;
import com.nightfall.englishnova.shared.dto.WordSearchResponseDto;
import com.nightfall.englishnova.shared.enums.VocabularyEntryType;

import java.util.List;

public interface SearchCatalogService {

    WordSearchResponseDto searchVocabulary(String keyword, CurrentUser user, Long wordbookId);

    List<SearchSuggestionDto> searchSuggestions(String keyword, CurrentUser user, Long wordbookId);

    WordDetailDto getWordDetail(long entryId, VocabularyEntryType entryType, CurrentUser user);

    List<PublicWordbookDto> listPublicWordbooks(CurrentUser user);

    List<PublicWordbookEntryDto> listPublicWordbookEntries(long publicWordbookId);

    PublicWordbookDto subscribePublicWordbook(long publicWordbookId, CurrentUser user);

    PublicWordbookDto unsubscribePublicWordbook(long publicWordbookId, CurrentUser user);

    PublicWordbookDto resetPublicWordbookProgress(long publicWordbookId, CurrentUser user);

    PublicCatalogImportResultDto importPublicCatalog(PublicCatalogImportRequest request);

    PublicCatalogImportJobDto createHighFrequencyPublicCatalogJob(PublicCatalogImportJobRequest request, CurrentUser user);

    PublicCatalogImportJobDto getPublicCatalogImportJob(long jobId);

    PublicCatalogImportJobDto retryFailedPublicCatalogImportJob(long jobId);

    PublicCatalogImportJobDto cancelPublicCatalogImportJob(long jobId);
}
