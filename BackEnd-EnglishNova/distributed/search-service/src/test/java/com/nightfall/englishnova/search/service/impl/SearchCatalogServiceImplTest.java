package com.nightfall.englishnova.search.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nightfall.englishnova.search.config.ExampleEnrichmentProperties;
import com.nightfall.englishnova.search.config.PublicCatalogSourceProperties;
import com.nightfall.englishnova.search.domain.vo.PublicCatalogImportJobVo;
import com.nightfall.englishnova.search.mapper.ExampleEnrichmentTaskMapper;
import com.nightfall.englishnova.search.mapper.PublicCatalogImportJobMapper;
import com.nightfall.englishnova.search.mapper.SearchVocabularyMapper;
import com.nightfall.englishnova.search.mapper.SearchWordbookMapper;
import com.nightfall.englishnova.search.service.ExampleAudioStorageService;
import com.nightfall.englishnova.search.service.OpenAiExampleEnrichmentClient;
import com.nightfall.englishnova.shared.auth.CurrentUser;
import com.nightfall.englishnova.shared.dto.PublicCatalogImportJobDto;
import com.nightfall.englishnova.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchCatalogServiceImplTest {

    @Mock
    private SearchVocabularyMapper searchVocabularyMapper;
    @Mock
    private SearchWordbookMapper searchWordbookMapper;
    @Mock
    private ExampleEnrichmentTaskMapper exampleEnrichmentTaskMapper;
    @Mock
    private PublicCatalogImportJobMapper publicCatalogImportJobMapper;
    @Mock
    private OpenAiExampleEnrichmentClient openAiExampleEnrichmentClient;
    @Mock
    private ExampleAudioStorageService exampleAudioStorageService;

    private SearchCatalogServiceImpl searchCatalogService;

    @BeforeEach
    void setUp() {
        searchCatalogService = new SearchCatalogServiceImpl(
                searchVocabularyMapper,
                searchWordbookMapper,
                exampleEnrichmentTaskMapper,
                publicCatalogImportJobMapper,
                new ExampleEnrichmentProperties(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null),
                new PublicCatalogSourceProperties(null),
                openAiExampleEnrichmentClient,
                exampleAudioStorageService,
                new ObjectMapper(),
                "http://localhost:9200",
                4,
                true
        );
    }

    @Test
    void getPublicCatalogImportJobRequiresCreatorOwnership() {
        when(publicCatalogImportJobMapper.findJobByIdAndCreator(9L, 1001L)).thenReturn(jobVo(9L, 1001L, "PENDING"));

        PublicCatalogImportJobDto result = searchCatalogService.getPublicCatalogImportJob(9L, new CurrentUser(1001L, "alice"));

        assertEquals(9L, result.id());
        assertEquals(1001L, result.createdByUserId());
    }

    @Test
    void getPublicCatalogImportJobReturnsNotFoundForNonOwner() {
        when(publicCatalogImportJobMapper.findJobByIdAndCreator(9L, 2002L)).thenReturn(null);

        assertThrows(NotFoundException.class,
                () -> searchCatalogService.getPublicCatalogImportJob(9L, new CurrentUser(2002L, "mallory")));
    }

    @Test
    void cancelPublicCatalogImportJobOnlyCancelsOwnedJob() {
        when(publicCatalogImportJobMapper.findJobByIdAndCreator(10L, 1001L))
                .thenReturn(jobVo(10L, 1001L, "RUNNING"))
                .thenReturn(jobVo(10L, 1001L, "CANCELLED"));

        PublicCatalogImportJobDto result =
                searchCatalogService.cancelPublicCatalogImportJob(10L, new CurrentUser(1001L, "alice"));

        verify(publicCatalogImportJobMapper).cancelJob(10L);
        assertEquals("CANCELLED", result.status());
    }

    @Test
    void containsTargetWordAcceptsCommonInflections() throws Exception {
        Method method = SearchCatalogServiceImpl.class.getDeclaredMethod("containsTargetWord", String.class, String.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(searchCatalogService,
                "It behoves all employees to follow the company's code of conduct.",
                "behove"));
        assertTrue((Boolean) method.invoke(searchCatalogService,
                "The old castle walls were covered with pockmarks from centuries of erosion.",
                "pockmark"));
    }

    @Test
    void containsTargetWordStillRespectsWordBoundaries() throws Exception {
        Method method = SearchCatalogServiceImpl.class.getDeclaredMethod("containsTargetWord", String.class, String.class);
        method.setAccessible(true);

        assertFalse((Boolean) method.invoke(searchCatalogService,
                "The article was pinned to the board.",
                "art"));
    }

    private PublicCatalogImportJobVo jobVo(long jobId, long creatorUserId, String status) {
        PublicCatalogImportJobVo row = new PublicCatalogImportJobVo();
        row.setId(jobId);
        row.setCreatedByUserId(creatorUserId);
        row.setSourceName("high-frequency");
        row.setStatus(status);
        row.setTotalWords(100);
        row.setBatchSize(20);
        return row;
    }
}
