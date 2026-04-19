package com.nightfall.englishnova.search.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nightfall.englishnova.search.domain.po.PublicEntryPo;
import com.nightfall.englishnova.search.domain.po.PublicCatalogImportJobPo;
import com.nightfall.englishnova.search.domain.po.PublicWordbookPo;
import com.nightfall.englishnova.search.domain.vo.DetailVo;
import com.nightfall.englishnova.search.domain.vo.ImportTaskCleanupVo;
import com.nightfall.englishnova.search.domain.vo.PublicCatalogImportItemVo;
import com.nightfall.englishnova.search.domain.vo.PublicCatalogImportJobVo;
import com.nightfall.englishnova.search.domain.vo.SearchDocumentVo;
import com.nightfall.englishnova.search.domain.vo.StudyFocusCleanupVo;
import com.nightfall.englishnova.search.domain.vo.VocabularyCleanupVo;
import com.nightfall.englishnova.search.domain.vo.WordbookCleanupVo;
import com.nightfall.englishnova.search.mapper.PublicCatalogImportJobMapper;
import com.nightfall.englishnova.search.mapper.SearchImportTaskMapper;
import com.nightfall.englishnova.search.mapper.SearchStudyFocusMapper;
import com.nightfall.englishnova.search.mapper.SearchVocabularyMapper;
import com.nightfall.englishnova.search.mapper.SearchWordbookMapper;
import com.nightfall.englishnova.search.service.SearchCatalogService;
import com.nightfall.englishnova.search.utools.SearchTextUtools;
import com.nightfall.englishnova.shared.auth.CurrentUser;
import com.nightfall.englishnova.shared.dto.PublicCatalogImportJobDto;
import com.nightfall.englishnova.shared.dto.PublicCatalogImportJobRequest;
import com.nightfall.englishnova.shared.dto.PublicCatalogImportRequest;
import com.nightfall.englishnova.shared.dto.PublicCatalogImportResultDto;
import com.nightfall.englishnova.shared.dto.SearchHitDto;
import com.nightfall.englishnova.shared.dto.SearchSuggestionDto;
import com.nightfall.englishnova.shared.dto.WordDetailDto;
import com.nightfall.englishnova.shared.dto.WordSearchResponseDto;
import com.nightfall.englishnova.shared.events.WordbookImportedEvent;
import com.nightfall.englishnova.shared.exception.ForbiddenException;
import com.nightfall.englishnova.shared.exception.NotFoundException;
import com.nightfall.englishnova.shared.text.PhoneticNormalizer;
import com.nightfall.englishnova.shared.text.TextRepairUtils;
import com.nightfall.englishnova.shared.text.UserFacingTextNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 Elasticsearch 的搜索目录服务。
 * 负责公共词库与私有词库搜索、词条详情查询，以及公共词库补全导入。
 */
@Service
public class SearchCatalogServiceImpl implements SearchCatalogService {

    private static final Logger log = LoggerFactory.getLogger(SearchCatalogServiceImpl.class);

    private static final String INDEX_NAME = "english-nova-words";
    private static final String PUBLIC_VISIBILITY = "PUBLIC";
    private static final String PRIVATE_VISIBILITY = "PRIVATE";
    private static final long PUBLIC_OWNER_USER_ID = 1103L;
    private static final String PUBLIC_SOURCE_LABEL = "Public Catalog - ECDICT";
    private static final String PRIVATE_SOURCE_LABEL = "My Wordbook";
    private static final String PUBLIC_WORDBOOK_NAME = "English Nova Public Catalog";
    private static final String PUBLIC_WORDBOOK_SOURCE = "ECDICT + dictionaryapi.dev";
    private static final String PUBLIC_WORDBOOK_PLATFORM = "ECDICT";
    private static final String PUBLIC_IMPORT_SOURCE = "ecdict";
    private static final String ECDICT_HIGH_FREQUENCY_RESOURCE = "public-catalog/ecdict-high-frequency-5000.tsv";
    private static final String HIGH_FREQUENCY_SOURCE_NAME = "ecdict-high-frequency-5000";
    private static final String JOB_STATUS_PENDING = "PENDING";
    private static final String JOB_STATUS_RUNNING = "RUNNING";
    private static final String JOB_STATUS_CANCELLED = "CANCELLED";
    private static final String ITEM_STATUS_IMPORTED = "IMPORTED";
    private static final String ITEM_STATUS_UPDATED = "UPDATED";
    private static final int MAX_IMPORT_WORDS = 500;
    private static final int MAX_HIGH_FREQUENCY_WORDS = 5000;
    private static final int DEFAULT_HIGH_FREQUENCY_LIMIT = 5000;
    private static final int DEFAULT_HIGH_FREQUENCY_BATCH_SIZE = 150;
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int SEARCH_RESULT_SIZE = 18;
    private static final int SEARCH_RESULT_FETCH_SIZE = 60;
    private static final int SUGGESTION_FETCH_SIZE = 40;
    private static final int SUGGESTION_LIMIT = 10;
    private static final String FREE_DICTIONARY_API_BASE_URL = "https://freedictionaryapi.com/api/v1";
    private static final String AUDIO_API_BASE_URL = "https://api.dictionaryapi.dev/api/v2/entries/en";

    private final SearchVocabularyMapper searchVocabularyMapper;
    private final SearchWordbookMapper searchWordbookMapper;
    private final PublicCatalogImportJobMapper publicCatalogImportJobMapper;
    private final SearchImportTaskMapper searchImportTaskMapper;
    private final SearchStudyFocusMapper searchStudyFocusMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String elasticsearchBaseUrl;
    private final AtomicBoolean importWorkerRunning = new AtomicBoolean(false);
    private volatile Map<String, EcdictCatalogEntry> ecdictCatalogCache;

    public SearchCatalogServiceImpl(
            SearchVocabularyMapper searchVocabularyMapper,
            SearchWordbookMapper searchWordbookMapper,
            PublicCatalogImportJobMapper publicCatalogImportJobMapper,
            SearchImportTaskMapper searchImportTaskMapper,
            SearchStudyFocusMapper searchStudyFocusMapper,
            ObjectMapper objectMapper,
            @Value("${spring.elasticsearch.uris}") String elasticsearchBaseUrl
    ) {
        this.searchVocabularyMapper = searchVocabularyMapper;
        this.searchWordbookMapper = searchWordbookMapper;
        this.publicCatalogImportJobMapper = publicCatalogImportJobMapper;
        this.searchImportTaskMapper = searchImportTaskMapper;
        this.searchStudyFocusMapper = searchStudyFocusMapper;
        this.objectMapper = objectMapper;
        this.elasticsearchBaseUrl = elasticsearchBaseUrl.endsWith("/")
                ? elasticsearchBaseUrl.substring(0, elasticsearchBaseUrl.length() - 1)
                : elasticsearchBaseUrl;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * 未指定词书时只搜索公共词库；指定词书时只搜索当前用户拥有的该词书。
     */
    public WordSearchResponseDto searchVocabulary(String keyword, CurrentUser user, Long wordbookId) {
        String normalizedKeyword = SearchTextUtools.normalizeSearchKeyword(keyword);
        if (normalizedKeyword.isBlank()) {
            return new WordSearchResponseDto(List.of());
        }

        SearchScope scope = resolveSearchScope(user, wordbookId);
        ensureIndex();
        List<SearchHitDto> hits = searchByScope(normalizedKeyword, scope.ownerUserId(), scope.visibility(), scope.wordbookId());
        if (hits.isEmpty() && scope.allowHydrate() && shouldHydrate(normalizedKeyword)) {
            importWords(List.of(normalizedKeyword), false);
            hits = searchByScope(normalizedKeyword, scope.ownerUserId(), scope.visibility(), scope.wordbookId());
        }
        return new WordSearchResponseDto(hits);
    }

    /**
     * 当关键字看起来像单词查询时，返回搜索建议。
     */
    public List<SearchSuggestionDto> searchSuggestions(String keyword, CurrentUser user, Long wordbookId) {
        String normalizedKeyword = SearchTextUtools.normalizeSearchKeyword(keyword);
        if (normalizedKeyword.isBlank() || !shouldHydrate(normalizedKeyword)) {
            return List.of();
        }

        SearchScope scope = resolveSearchScope(user, wordbookId);
        ensureIndex();
        return searchSuggestionsByWordMatch(normalizedKeyword, scope.ownerUserId(), scope.visibility(), scope.wordbookId());
    }

    /**
     * 加载单个词条详情，并在条件满足时懒加载补全音频地址。
     */
    public WordDetailDto getWordDetail(long entryId, CurrentUser user) {
        DetailVo row = loadDetailRow(entryId);
        if (PRIVATE_VISIBILITY.equalsIgnoreCase(row.getVisibility())
                && (user == null || row.getOwnerUserId() == null || row.getOwnerUserId() != user.id())) {
            throw new ForbiddenException("You cannot access this word");
        }

        String normalizedWord = TextRepairUtils.repair(row.getWord());
        String audioUrl = SearchTextUtools.normalizeAudioUrl(row.getAudioUrl());
        if (audioUrl.isBlank() && shouldHydrate(normalizedWord)) {
            audioUrl = SearchTextUtools.normalizeAudioUrl(fetchAudioUrl(normalizedWord));
        }
        if (!audioUrl.isBlank() && !sameText(row.getAudioUrl(), audioUrl)) {
            searchVocabularyMapper.updateAudioUrl(entryId, audioUrl);
        }

        return new WordDetailDto(
                row.getEntryId(),
                row.getOwnerUserId(),
                row.getWordbookId(),
                UserFacingTextNormalizer.normalizeDisplayText(row.getWordbookName()),
                normalizedWord,
                SearchTextUtools.normalizePhonetic(row.getPhonetic()),
                UserFacingTextNormalizer.normalizeMeaningText(row.getMeaningCn()),
                UserFacingTextNormalizer.normalizeDisplayText(row.getExampleSentence()),
                UserFacingTextNormalizer.normalizeMeaningText(row.getCategory()),
                UserFacingTextNormalizer.normalizeDisplayText(row.getDefinitionEn()),
                UserFacingTextNormalizer.normalizeDisplayText(row.getTags()),
                row.getBncRank(),
                row.getFrqRank(),
                row.getWordfreqZipf(),
                UserFacingTextNormalizer.normalizeDisplayText(row.getExchangeInfo()),
                UserFacingTextNormalizer.normalizeDisplayText(row.getDataQuality()),
                row.getDifficulty(),
                row.getVisibility(),
                buildSourceLabel(row.getVisibility()),
                UserFacingTextNormalizer.normalizeDisplayText(row.getSourceName()),
                SearchTextUtools.normalizeImportSource(row.getImportSource()),
                audioUrl
        );
    }

    /**
     * 将单词导入共享公共词库。
     */
    public PublicCatalogImportResultDto importPublicCatalog(PublicCatalogImportRequest request) {
        boolean refreshExisting = request != null && Boolean.TRUE.equals(request.refreshExisting());
        List<String> normalizedWords = SearchTextUtools.normalizeWords(request == null ? null : request.words(), MAX_IMPORT_WORDS);
        if (normalizedWords.isEmpty()) {
            normalizedWords = defaultSeedWords();
        }
        return importWords(normalizedWords, refreshExisting);
    }

    public PublicCatalogImportJobDto createHighFrequencyPublicCatalogJob(
            PublicCatalogImportJobRequest request,
            CurrentUser user
    ) {
        int resolvedLimit = clampPositive(request == null ? null : request.limit(), DEFAULT_HIGH_FREQUENCY_LIMIT, MAX_HIGH_FREQUENCY_WORDS);
        int resolvedBatchSize = clampPositive(request == null ? null : request.batchSize(), DEFAULT_HIGH_FREQUENCY_BATCH_SIZE, MAX_IMPORT_WORDS);
        boolean shouldRefreshExisting = request != null && Boolean.TRUE.equals(request.refreshExisting());

        List<String> words = loadHighFrequencyWords();
        if (words.size() > resolvedLimit) {
            words = words.subList(0, resolvedLimit);
        }

        PublicCatalogImportJobPo job = new PublicCatalogImportJobPo(
                null,
                HIGH_FREQUENCY_SOURCE_NAME,
                JOB_STATUS_PENDING,
                words.size(),
                shouldRefreshExisting,
                resolvedBatchSize,
                user == null ? null : user.id()
        );
        publicCatalogImportJobMapper.insertJob(job);
        if (!words.isEmpty()) {
            publicCatalogImportJobMapper.insertItems(job.getId(), words);
        }
        return requireImportJob(job.getId());
    }

    public PublicCatalogImportJobDto getPublicCatalogImportJob(long jobId) {
        return requireImportJob(jobId);
    }

    public PublicCatalogImportJobDto retryFailedPublicCatalogImportJob(long jobId) {
        PublicCatalogImportJobDto job = requireImportJob(jobId);
        if (JOB_STATUS_CANCELLED.equals(job.status())) {
            throw new ForbiddenException("Cancelled public catalog import jobs cannot be retried");
        }
        publicCatalogImportJobMapper.resetFailedItems(job.id());
        publicCatalogImportJobMapper.refreshJobCounters(job.id());
        PublicCatalogImportJobVo row = publicCatalogImportJobMapper.findJob(job.id());
        if ("FAILED".equals(row.getStatus()) || "COMPLETED".equals(row.getStatus())) {
            publicCatalogImportJobMapper.startJob(job.id());
        }
        return requireImportJob(job.id());
    }

    public PublicCatalogImportJobDto cancelPublicCatalogImportJob(long jobId) {
        PublicCatalogImportJobDto job = requireImportJob(jobId);
        publicCatalogImportJobMapper.cancelJob(job.id());
        return requireImportJob(job.id());
    }

    @Scheduled(fixedDelayString = "${english-nova.search.public-catalog-import-worker-delay-ms:5000}")
    public void processPublicCatalogImportJobs() {
        if (!importWorkerRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            PublicCatalogImportJobVo job = publicCatalogImportJobMapper.findNextRunnableJob();
            if (job == null) {
                return;
            }
            processPublicCatalogImportJob(job);
        } finally {
            importWorkerRunning.set(false);
        }
    }

    private void processPublicCatalogImportJob(PublicCatalogImportJobVo job) {
        publicCatalogImportJobMapper.startJob(job.getId());
        PublicCatalogImportJobVo currentJob = publicCatalogImportJobMapper.findJob(job.getId());
        if (currentJob == null) {
            return;
        }
        publicCatalogImportJobMapper.resetRunningItems(currentJob.getId());

        List<PublicCatalogImportItemVo> items = publicCatalogImportJobMapper.claimPendingItems(
                currentJob.getId(),
                Math.max(1, Math.min(currentJob.getBatchSize(), MAX_IMPORT_WORDS))
        );
        if (items.isEmpty()) {
            publicCatalogImportJobMapper.refreshJobCounters(currentJob.getId());
            publicCatalogImportJobMapper.completeJobIfFinished(currentJob.getId());
            return;
        }

        boolean elasticsearchAvailable = tryEnsureIndex();
        boolean databaseChanged = false;
        boolean indexChanged = false;
        try {
            for (PublicCatalogImportItemVo item : items) {
                if (publicCatalogImportJobMapper.markItemRunning(item.getId()) == 0) {
                    continue;
                }
                try {
                    ImportOutcome outcome = importSingleWord(
                            item.getWord(),
                            currentJob.isRefreshExisting(),
                            elasticsearchAvailable
                    );
                    switch (outcome.action()) {
                        case IMPORTED -> {
                            publicCatalogImportJobMapper.markItemImported(item.getId(), outcome.entryId(), ITEM_STATUS_IMPORTED);
                            databaseChanged = true;
                            indexChanged = true;
                        }
                        case UPDATED -> {
                            publicCatalogImportJobMapper.markItemImported(item.getId(), outcome.entryId(), ITEM_STATUS_UPDATED);
                            databaseChanged = true;
                            indexChanged = true;
                        }
                        case SKIPPED -> publicCatalogImportJobMapper.markItemSkipped(item.getId(), outcome.entryId());
                        case FAILED -> publicCatalogImportJobMapper.markItemFailed(item.getId(), outcome.errorMessage());
                    }
                } catch (Exception exception) {
                    publicCatalogImportJobMapper.markItemFailed(item.getId(), normalizeErrorMessage(exception));
                }
            }

            if (databaseChanged) {
                Long wordbookId = findPublicWordbookId();
                if (wordbookId != null) {
                    syncPublicWordbookCount(wordbookId);
                }
            }
            if (indexChanged && elasticsearchAvailable) {
                safeRefreshIndex();
            }
            publicCatalogImportJobMapper.refreshJobCounters(currentJob.getId());
            publicCatalogImportJobMapper.completeJobIfFinished(currentJob.getId());
        } catch (Exception exception) {
            publicCatalogImportJobMapper.failJob(currentJob.getId(), normalizeErrorMessage(exception));
        }
    }

    private PublicCatalogImportJobDto requireImportJob(long jobId) {
        PublicCatalogImportJobVo row = publicCatalogImportJobMapper.findJob(jobId);
        if (row == null) {
            throw new NotFoundException("Public catalog import job not found");
        }
        return toImportJobDto(row);
    }

    private PublicCatalogImportJobDto toImportJobDto(PublicCatalogImportJobVo row) {
        return new PublicCatalogImportJobDto(
                row.getId(),
                row.getSourceName(),
                row.getStatus(),
                row.getTotalWords(),
                row.getProcessedWords(),
                row.getImportedWords(),
                row.getUpdatedWords(),
                row.getSkippedWords(),
                row.getFailedWords(),
                row.isRefreshExisting(),
                row.getBatchSize(),
                row.getStartedAt(),
                row.getFinishedAt(),
                row.getCreatedByUserId(),
                row.getErrorMessage(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }

    private String normalizeErrorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        message = UserFacingTextNormalizer.normalizeDisplayText(message);
        return message.length() > 240 ? message.substring(0, 240) : message;
    }

    /**
     * 应用启动后重建整套搜索索引。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void rebuildAll() {
        int updatedRows = normalizeDatabaseContent();
        if (updatedRows > 0) {
            log.info("Normalized {} database rows to simplified Chinese before rebuilding Elasticsearch", updatedRows);
        }

        try {
            deleteIndex();
            createIndex();
            loadAllRows().forEach(this::indexDocument);
            refreshIndex();
        } catch (IOException | InterruptedException exception) {
            log.warn("Skipping Elasticsearch rebuild because the cluster is unavailable: {}", exception.getMessage());
        }
    }

    /**
     * 在词书导入完成后，将数据同步到 Elasticsearch。
     */
    @RabbitListener(queues = "${english-nova.search.index-queue}")
    public void handleImportedWordbook(WordbookImportedEvent event) {
        if (!tryEnsureIndex()) {
            log.warn("Skipping Elasticsearch sync for imported wordbook {} because the cluster is unavailable", event.wordbookId());
            return;
        }
        searchVocabularyMapper.listByUserAndWordbook(event.userId(), event.wordbookId()).forEach(this::indexDocument);
        safeRefreshIndex();
    }

    // 在指定可见性范围内查询 Elasticsearch。
    private List<SearchHitDto> searchByScope(String keyword, Long ownerUserId, String visibility, Long wordbookId) {
        try {
            String normalizedWordKeyword = normalizeIndexedWord(keyword);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("size", SEARCH_RESULT_FETCH_SIZE);
            body.put("_source", List.of(
                    "entryId", "word", "phonetic", "meaningCn", "exampleSentence", "category",
                    "definitionEn", "tags", "bncRank", "frqRank", "wordfreqZipf", "dataQuality",
                    "visibility", "importSource"
            ));

            List<Object> filters = buildScopeFilters(ownerUserId, visibility, wordbookId);

            List<Object> shouldQueries = new ArrayList<>();
            shouldQueries.add(buildTextSearchQuery(keyword));
            if (supportsWordMatching(keyword)) {
                shouldQueries.addAll(buildWordSearchQueries(normalizedWordKeyword));
            }

            body.put("query", Map.of(
                    "bool", Map.of(
                            "filter", filters,
                            "should", shouldQueries,
                            "minimum_should_match", 1
                    )
            ));

            JsonNode response = sendJson("POST", "/" + INDEX_NAME + "/_search", body, false);
            return toSearchHits(response.path("hits").path("hits"), keyword);
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException("Failed to query Elasticsearch", exception);
        }
    }

    // 未登录用户只能看公共词条；已登录用户还能看见自己的私有词条。
    private Object buildSuggestionVisibilityFilter(CurrentUser user) {
        if (user == null) {
            return Map.of("term", Map.of("visibility", PUBLIC_VISIBILITY));
        }

        return Map.of(
                "bool", Map.of(
                        "should", List.of(
                                Map.of("term", Map.of("visibility", PUBLIC_VISIBILITY)),
                                Map.of("bool", Map.of(
                                        "filter", List.of(
                                                Map.of("term", Map.of("visibility", PRIVATE_VISIBILITY)),
                                                Map.of("term", Map.of("ownerUserId", user.id()))
                                        )
                                ))
                        ),
                        "minimum_should_match", 1
                )
        );
    }

    private List<SearchSuggestionDto> searchSuggestionsByWordMatch(
            String keyword,
            Long ownerUserId,
            String visibility,
            Long wordbookId
    ) {
        try {
            String normalizedWordKeyword = normalizeIndexedWord(keyword);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("size", SUGGESTION_FETCH_SIZE);
            body.put("_source", List.of("entryId", "word", "visibility", "ownerUserId"));
            body.put("query", Map.of(
                    "bool", Map.of(
                            "filter", buildScopeFilters(ownerUserId, visibility, wordbookId),
                            "should", buildWordSearchQueries(normalizedWordKeyword),
                            "minimum_should_match", 1
                    )
            ));

            JsonNode response = sendJson("POST", "/" + INDEX_NAME + "/_search", body, false);
            return toSearchSuggestions(response.path("hits").path("hits"), null, keyword);
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException("Failed to query Elasticsearch suggestions", exception);
        }
    }

    // 按单词去重搜索建议，并保留排序结果最优的一项。
    private List<SearchSuggestionDto> toSearchSuggestions(JsonNode hits, CurrentUser user, String keyword) {
        List<SuggestionCandidate> candidates = new ArrayList<>();
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            MatchType matchType = determineSuggestionMatchType(TextRepairUtils.repair(source.path("word").asText()), keyword);
            if (matchType == null) {
                continue;
            }
            candidates.add(new SuggestionCandidate(
                    source.path("entryId").asLong(),
                    TextRepairUtils.repair(source.path("word").asText()),
                    source.path("visibility").asText(),
                    source.path("ownerUserId").isMissingNode() || source.path("ownerUserId").isNull() ? null : source.path("ownerUserId").asLong(),
                    hit.path("_score").asDouble(0),
                    matchType
            ));
        }

        if (candidates.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<String, SuggestionCandidate> deduplicated = new LinkedHashMap<>();
        for (SuggestionCandidate candidate : candidates) {
            String key = candidate.word().toLowerCase(Locale.ROOT);
            SuggestionCandidate existing = deduplicated.get(key);
            if (existing == null || shouldReplaceSuggestion(existing, candidate, user)) {
                deduplicated.put(key, candidate);
            }
        }

        List<SuggestionCandidate> ordered = new ArrayList<>(deduplicated.values());
        ordered.sort(suggestionComparator());
        List<SearchSuggestionDto> result = new ArrayList<>();
        for (SuggestionCandidate candidate : ordered) {
            result.add(new SearchSuggestionDto(
                    candidate.entryId(),
                    candidate.word(),
                    candidate.visibility(),
                    candidate.matchType().matchPercent(),
                    candidate.matchType().name()
            ));
            if (result.size() >= SUGGESTION_LIMIT) {
                break;
            }
        }
        return result;
    }

    private List<SearchHitDto> toSearchHits(JsonNode hits, String keyword) {
        List<SearchHitCandidate> candidates = new ArrayList<>();
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            candidates.add(new SearchHitCandidate(
                    source.path("entryId").asLong(),
                    TextRepairUtils.repair(source.path("word").asText()),
                    SearchTextUtools.normalizePhonetic(source.path("phonetic").asText()),
                    UserFacingTextNormalizer.normalizeMeaningText(source.path("meaningCn").asText()),
                    buildSourceLabel(source.path("visibility").asText()),
                    UserFacingTextNormalizer.normalizeDisplayText(source.path("exampleSentence").asText()),
                    UserFacingTextNormalizer.normalizeMeaningText(source.path("category").asText()),
                    UserFacingTextNormalizer.normalizeDisplayText(source.path("definitionEn").asText()),
                    UserFacingTextNormalizer.normalizeDisplayText(source.path("tags").asText()),
                    source.path("bncRank").isMissingNode() || source.path("bncRank").isNull() ? null : source.path("bncRank").asInt(),
                    source.path("frqRank").isMissingNode() || source.path("frqRank").isNull() ? null : source.path("frqRank").asInt(),
                    source.path("wordfreqZipf").isMissingNode() || source.path("wordfreqZipf").isNull() ? null : source.path("wordfreqZipf").asDouble(),
                    UserFacingTextNormalizer.normalizeDisplayText(source.path("dataQuality").asText()),
                    source.path("visibility").asText(),
                    SearchTextUtools.normalizeImportSource(source.path("importSource").asText()),
                    hit.path("_score").asDouble(0)
            ));
        }

        if (candidates.isEmpty()) {
            return List.of();
        }

        List<RankedSearchHit> rankedHits = new ArrayList<>();
        for (SearchHitCandidate candidate : candidates) {
            MatchType matchType = determineHitMatchType(candidate, keyword);
            if (matchType == null) {
                continue;
            }
            rankedHits.add(new RankedSearchHit(
                    candidate.entryId(),
                    candidate.word(),
                    candidate.phonetic(),
                    candidate.meaningCn(),
                    candidate.source(),
                    candidate.exampleSentence(),
                    candidate.category(),
                    candidate.definitionEn(),
                    candidate.tags(),
                    candidate.bncRank(),
                    candidate.frqRank(),
                    candidate.wordfreqZipf(),
                    candidate.dataQuality(),
                    candidate.visibility(),
                    candidate.importSource(),
                    candidate.score(),
                    matchType
            ));
        }

        rankedHits.sort(searchHitComparator());
        List<SearchHitDto> result = new ArrayList<>();
        for (RankedSearchHit hit : rankedHits) {
            result.add(new SearchHitDto(
                    hit.entryId(),
                    hit.word(),
                    hit.phonetic(),
                    hit.meaningCn(),
                    hit.source(),
                    hit.exampleSentence(),
                    hit.category(),
                    hit.definitionEn(),
                    hit.tags(),
                    frequencyRank(hit.bncRank(), hit.frqRank()),
                    hit.wordfreqZipf(),
                    hit.dataQuality(),
                    hit.visibility(),
                    hit.importSource(),
                    hit.matchType().matchPercent(),
                    hit.matchType().name()
            ));
            if (result.size() >= SEARCH_RESULT_SIZE) {
                break;
            }
        }
        return result;
    }

    private Map<String, Object> buildTextSearchQuery(String keyword) {
        return Map.of("multi_match", Map.of(
                "query", keyword,
                "fields", List.of("meaningCn^3", "category^2", "exampleSentence", "definitionEn^0.5"),
                "type", "best_fields"
        ));
    }

    private boolean shouldReplaceSuggestion(SuggestionCandidate existing, SuggestionCandidate candidate, CurrentUser user) {
        if (user == null) {
            return suggestionComparator().compare(candidate, existing) < 0;
        }

        boolean existingIsMine = PRIVATE_VISIBILITY.equalsIgnoreCase(existing.visibility())
                && existing.ownerUserId() != null
                && existing.ownerUserId() == user.id();
        boolean candidateIsMine = PRIVATE_VISIBILITY.equalsIgnoreCase(candidate.visibility())
                && candidate.ownerUserId() != null
                && candidate.ownerUserId() == user.id();
        if (candidateIsMine != existingIsMine) {
            return candidateIsMine;
        }
        return suggestionComparator().compare(candidate, existing) < 0;
    }

    private List<Object> buildWordSearchQueries(String normalizedWordKeyword) {
        List<Object> queries = new ArrayList<>();
        queries.add(Map.of("term", Map.of(
                "wordExact", Map.of(
                        "value", normalizedWordKeyword,
                        "boost", 120
                )
        )));
        queries.add(Map.of("prefix", Map.of(
                "wordExact", Map.of(
                        "value", normalizedWordKeyword,
                        "boost", 80
                )
        )));
        queries.add(Map.of("wildcard", Map.of(
                "wordWildcard", Map.of(
                        "value", "*" + normalizedWordKeyword + "*",
                        "boost", 40
                )
        )));
        queries.add(Map.of("multi_match", Map.of(
                "query", normalizedWordKeyword,
                "type", "bool_prefix",
                "fields", List.of("wordSuggest^30", "wordSuggest._2gram^20", "wordSuggest._3gram^10")
        )));
        return queries;
    }

    private MatchType determineHitMatchType(SearchHitCandidate candidate, String keyword) {
        MatchType wordMatchType = determineSuggestionMatchType(candidate.word(), keyword);
        if (wordMatchType != null) {
            return wordMatchType;
        }
        if (containsNormalizedText(candidate.meaningCn(), keyword)
                || containsNormalizedText(candidate.category(), keyword)
                || containsNormalizedText(candidate.exampleSentence(), keyword)) {
            return MatchType.TEXT;
        }
        return null;
    }

    private MatchType determineSuggestionMatchType(String word, String keyword) {
        if (!supportsWordMatching(keyword)) {
            return null;
        }
        String normalizedWord = normalizeIndexedWord(word);
        String normalizedKeyword = normalizeIndexedWord(keyword);
        if (normalizedWord.isEmpty() || normalizedKeyword.isEmpty()) {
            return null;
        }
        if (normalizedWord.equals(normalizedKeyword)) {
            return MatchType.EXACT;
        }
        if (normalizedWord.startsWith(normalizedKeyword)) {
            return MatchType.PREFIX;
        }
        if (normalizedWord.contains(normalizedKeyword)) {
            return MatchType.CONTAINS;
        }
        return null;
    }

    private boolean containsNormalizedText(String value, String keyword) {
        if (value == null || value.isBlank() || keyword == null || keyword.isBlank()) {
            return false;
        }
        return UserFacingTextNormalizer.normalizeDisplayText(value)
                .toLowerCase(Locale.ROOT)
                .contains(keyword.toLowerCase(Locale.ROOT));
    }

    private boolean supportsWordMatching(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return false;
        }
        return normalizeIndexedWord(keyword).matches("[a-z][a-z\\-']*");
    }

    private String normalizeIndexedWord(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return TextRepairUtils.repair(value).trim().toLowerCase(Locale.ROOT);
    }

    private List<Object> buildScopeFilters(Long ownerUserId, String visibility, Long wordbookId) {
        List<Object> filters = new ArrayList<>();
        filters.add(Map.of("term", Map.of("visibility", visibility)));
        if (ownerUserId != null) {
            filters.add(Map.of("term", Map.of("ownerUserId", ownerUserId)));
        }
        if (wordbookId != null) {
            filters.add(Map.of("term", Map.of("wordbookId", wordbookId)));
        }
        return filters;
    }

    private SearchScope resolveSearchScope(CurrentUser user, Long wordbookId) {
        if (wordbookId == null) {
            return new SearchScope(null, PUBLIC_VISIBILITY, null, true);
        }
        if (user == null || searchWordbookMapper.countOwnedWordbook(user.id(), wordbookId) == 0) {
            throw new ForbiddenException("You cannot access this wordbook");
        }
        return new SearchScope(user.id(), PRIVATE_VISIBILITY, wordbookId, false);
    }

    private Comparator<RankedSearchHit> searchHitComparator() {
        return Comparator
                .comparingInt((RankedSearchHit hit) -> hit.matchType() == MatchType.TEXT ? 1 : 0)
                .thenComparingInt(hit -> hit.word().length())
                .thenComparingInt(hit -> hit.matchType().rank())
                .thenComparing(Comparator.comparingDouble(RankedSearchHit::score).reversed())
                .thenComparing(hit -> normalizeIndexedWord(hit.word()))
                .thenComparingLong(RankedSearchHit::entryId);
    }

    private Comparator<SuggestionCandidate> suggestionComparator() {
        return Comparator
                .comparingInt((SuggestionCandidate candidate) -> candidate.word().length())
                .thenComparingInt(candidate -> candidate.matchType().rank())
                .thenComparing(Comparator.comparingDouble(SuggestionCandidate::score).reversed())
                .thenComparing(candidate -> normalizeIndexedWord(candidate.word()))
                .thenComparingLong(SuggestionCandidate::entryId);
    }

    private List<String> loadHighFrequencyWords() {
        Map<String, EcdictCatalogEntry> catalog = loadEcdictCatalog();
        return new ArrayList<>(catalog.keySet()).subList(0, Math.min(MAX_HIGH_FREQUENCY_WORDS, catalog.size()));
    }

    private Map<String, EcdictCatalogEntry> loadEcdictCatalog() {
        Map<String, EcdictCatalogEntry> cached = ecdictCatalogCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (ecdictCatalogCache != null) {
                return ecdictCatalogCache;
            }
            ecdictCatalogCache = readEcdictCatalog();
            return ecdictCatalogCache;
        }
    }

    private Map<String, EcdictCatalogEntry> readEcdictCatalog() {
        ClassPathResource resource = new ClassPathResource(ECDICT_HIGH_FREQUENCY_RESOURCE);
        LinkedHashMap<String, EcdictCatalogEntry> entries = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null && entries.size() < MAX_HIGH_FREQUENCY_WORDS) {
                if (firstLine) {
                    firstLine = false;
                    if (line.startsWith("word\t")) {
                        continue;
                    }
                }
                EcdictCatalogEntry entry = parseEcdictCatalogLine(line);
                if (entry == null) {
                    continue;
                }
                entries.putIfAbsent(normalizeIndexedWord(entry.word()), entry);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load ECDICT high frequency resource", exception);
        }
        if (entries.isEmpty()) {
            throw new IllegalStateException("ECDICT high frequency resource is empty");
        }
        return entries;
    }

    private EcdictCatalogEntry parseEcdictCatalogLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] columns = line.split("\t", -1);
        if (columns.length < 12) {
            return null;
        }
        String word = normalizeIndexedWord(columns[0]);
        if (!supportsWordMatching(word)) {
            return null;
        }
        return new EcdictCatalogEntry(
                word,
                SearchTextUtools.normalizePhonetic(columns[1]),
                truncateText(UserFacingTextNormalizer.normalizeMeaningText(columns[2]), 255),
                truncateText(UserFacingTextNormalizer.normalizeMeaningText(columns[3]), 120),
                UserFacingTextNormalizer.normalizeDisplayText(columns[4]),
                UserFacingTextNormalizer.normalizeDisplayText(columns[5]),
                parseInteger(columns[6]),
                parseInteger(columns[7]),
                parseDouble(columns[8]),
                UserFacingTextNormalizer.normalizeDisplayText(columns[9]),
                UserFacingTextNormalizer.normalizeDisplayText(columns[10]),
                UserFacingTextNormalizer.normalizeDisplayText(columns[11])
        );
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (hasCleanText(value)) {
                return UserFacingTextNormalizer.normalizeDisplayText(value);
            }
        }
        return "";
    }

    private String truncateText(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = TextRepairUtils.repair(value).trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength).trim();
    }

    private Integer frequencyRank(Integer bncRank, Integer frqRank) {
        if (bncRank != null && bncRank > 0) {
            return bncRank;
        }
        return frqRank;
    }

    private int scoreDifficulty(EcdictCatalogEntry entry) {
        if (entry.wordfreqZipf() != null) {
            double zipf = entry.wordfreqZipf();
            if (zipf >= 5.5) {
                return 1;
            }
            if (zipf >= 4.7) {
                return 2;
            }
            if (zipf >= 3.8) {
                return 3;
            }
            if (zipf >= 3.0) {
                return 4;
            }
            return 5;
        }
        Integer frequencyRank = frequencyRank(entry.bncRank(), entry.frqRank());
        if (frequencyRank != null && frequencyRank > 0) {
            if (frequencyRank <= 2000) {
                return 1;
            }
            if (frequencyRank <= 5000) {
                return 2;
            }
            if (frequencyRank <= 10000) {
                return 3;
            }
            if (frequencyRank <= 20000) {
                return 4;
            }
            return 5;
        }
        return SearchTextUtools.scoreDifficulty(entry.word());
    }

    private int clampPositive(Integer value, int defaultValue, int maxValue) {
        if (value == null || value <= 0) {
            return defaultValue;
        }
        return Math.min(value, maxValue);
    }

    private PublicCatalogImportResultDto emptyImportResult(int requestedWords) {
        return new PublicCatalogImportResultDto(
                requestedWords,
                0,
                0,
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private PublicCatalogImportResultDto mergeImportResults(PublicCatalogImportResultDto left, PublicCatalogImportResultDto right) {
        List<String> imported = mergeLists(left.imported(), right.imported());
        List<String> updated = mergeLists(left.updated(), right.updated());
        List<String> skipped = mergeLists(left.skipped(), right.skipped());
        List<String> failed = mergeLists(left.failed(), right.failed());
        return new PublicCatalogImportResultDto(
                left.requestedWords(),
                imported.size(),
                updated.size(),
                skipped.size(),
                failed.size(),
                imported,
                updated,
                skipped,
                failed
        );
    }

    private List<String> mergeLists(List<String> left, List<String> right) {
        List<String> merged = new ArrayList<>(left.size() + right.size());
        merged.addAll(left);
        merged.addAll(right);
        return merged;
    }

    private PublicCatalogImportResultDto importWords(List<String> words, boolean refreshExisting) {
        boolean elasticsearchAvailable = tryEnsureIndex();
        List<String> imported = new ArrayList<>();
        List<String> updated = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        boolean indexChanged = false;
        boolean databaseChanged = false;

        for (String word : words) {
            try {
                ImportOutcome outcome = importSingleWord(word, refreshExisting, elasticsearchAvailable);
                switch (outcome.action()) {
                    case IMPORTED -> {
                        imported.add(word);
                        indexChanged = true;
                        databaseChanged = true;
                    }
                    case UPDATED -> {
                        updated.add(word);
                        indexChanged = true;
                        databaseChanged = true;
                    }
                    case SKIPPED -> skipped.add(word);
                    case FAILED -> failed.add(word);
                }
            } catch (Exception exception) {
                failed.add(word);
            }
        }

        if (databaseChanged) {
            Long wordbookId = findPublicWordbookId();
            if (wordbookId != null) {
                syncPublicWordbookCount(wordbookId);
            }
        }
        if (indexChanged && elasticsearchAvailable) {
            safeRefreshIndex();
        }

        return new PublicCatalogImportResultDto(
                words.size(),
                imported.size(),
                updated.size(),
                skipped.size(),
                failed.size(),
                imported,
                updated,
                skipped,
                failed
        );
    }

    // 获取一个词典结果，并规范化成数据库落库需要的字段。
    private DictionaryEntryPayload fetchDictionaryEntry(String word) {
        EcdictCatalogEntry entry = loadEcdictCatalog().get(normalizeIndexedWord(word));
        if (entry == null) {
            return null;
        }

        DictionaryApiExtras dictionaryApiExtras = fetchDictionaryApiExtras(entry.word());
        String example = firstNonBlank(
                entry.exampleSentence(),
                dictionaryApiExtras.exampleSentence(),
                fetchFreeDictionaryApiExample(entry.word())
        );
        String audioUrl = SearchTextUtools.normalizeAudioUrl(dictionaryApiExtras.audioUrl());

        if (!isCompletePublicEntryPayload(
                entry.word(),
                entry.phonetic(),
                entry.meaningCn(),
                example,
                entry.category(),
                entry.definitionEn(),
                audioUrl
        )) {
            return null;
        }

        return new DictionaryEntryPayload(
                entry.word(),
                entry.phonetic(),
                entry.meaningCn(),
                truncateText(example, 255),
                entry.category(),
                entry.definitionEn(),
                entry.tags(),
                entry.bncRank(),
                entry.frqRank(),
                entry.wordfreqZipf(),
                entry.exchangeInfo(),
                entry.dataQuality(),
                scoreDifficulty(entry),
                audioUrl,
                PUBLIC_IMPORT_SOURCE
        );
    }

    // 当当前词条没有音频时，从 dictionaryapi.dev 拉取音频地址。
    private String fetchAudioUrl(String word) {
        return fetchDictionaryApiExtras(word).audioUrl();
    }

    private DictionaryApiExtras fetchDictionaryApiExtras(String word) {
        try {
            String encodedWord = URLEncoder.encode(word, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(AUDIO_API_BASE_URL + "/" + encodedWord))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400 || response.body() == null || response.body().isBlank()) {
                return new DictionaryApiExtras("", "");
            }

            JsonNode entries = objectMapper.readTree(response.body());
            if (!entries.isArray()) {
                return new DictionaryApiExtras("", "");
            }

            String example = "";
            String audioUrl = "";
            for (JsonNode entry : entries) {
                if (example.isBlank()) {
                    example = extractDictionaryApiExample(entry.path("meanings"));
                }
                JsonNode phonetics = entry.path("phonetics");
                if (!phonetics.isArray()) {
                    continue;
                }
                for (JsonNode phonetic : phonetics) {
                    String audio = phonetic.path("audio").asText();
                    if (audio != null && !audio.isBlank()) {
                        audioUrl = audio;
                        break;
                    }
                }
                if (!example.isBlank() && !audioUrl.isBlank()) {
                    break;
                }
            }
            return new DictionaryApiExtras(
                    UserFacingTextNormalizer.normalizeDisplayText(example),
                    SearchTextUtools.normalizeAudioUrl(audioUrl)
            );
        } catch (IOException | InterruptedException exception) {
            return new DictionaryApiExtras("", "");
        }
    }

    private String extractDictionaryApiExample(JsonNode meanings) {
        if (!meanings.isArray()) {
            return "";
        }
        for (JsonNode meaning : meanings) {
            JsonNode definitions = meaning.path("definitions");
            if (!definitions.isArray()) {
                continue;
            }
            for (JsonNode definition : definitions) {
                String example = definition.path("example").asText();
                if (example != null && !example.isBlank()) {
                    return example;
                }
            }
        }
        return "";
    }

    private String fetchFreeDictionaryApiExample(String word) {
        try {
            String encodedWord = URLEncoder.encode(word, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(FREE_DICTIONARY_API_BASE_URL + "/entries/en/" + encodedWord))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400 || response.body() == null || response.body().isBlank()) {
                return "";
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode entries = root.path("entries");
            if (!entries.isArray()) {
                return "";
            }
            for (JsonNode entry : entries) {
                String example = extractExample(entry.path("senses"));
                if (!example.isBlank()) {
                    return UserFacingTextNormalizer.normalizeDisplayText(example);
                }
            }
            return "";
        } catch (IOException | InterruptedException exception) {
            return "";
        }
    }

    private boolean isCompletePublicEntryPayload(
            String word,
            String phonetic,
            String meaningCn,
            String exampleSentence,
            String category,
            String definitionEn,
            String audioUrl
    ) {
        return hasCleanText(word)
                && hasValidPhonetic(phonetic)
                && hasCleanText(meaningCn)
                && containsHanCharacter(meaningCn)
                && hasCleanText(exampleSentence)
                && hasCleanText(category)
                && hasCleanText(definitionEn)
                && hasCleanText(audioUrl)
                && audioUrl.startsWith("http");
    }

    private boolean hasCleanText(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.indexOf('\uFFFD') < 0
                && !value.contains("\u951f")
                && !value.contains("\u95bf")
                && !value.contains("\u9369")
                && !value.contains("???");
    }

    private boolean hasValidPhonetic(String phonetic) {
        return !PhoneticNormalizer.hasPlaceholder(phonetic);
    }

    private String extractPhonetic(JsonNode pronunciations) {
        if (!pronunciations.isArray()) {
            return "";
        }
        for (JsonNode pronunciation : pronunciations) {
            String type = pronunciation.path("type").asText();
            String text = pronunciation.path("text").asText();
            if ("ipa".equalsIgnoreCase(type) && text != null && !text.isBlank()) {
                return text;
            }
        }
        for (JsonNode pronunciation : pronunciations) {
            String text = pronunciation.path("text").asText();
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private void collectChineseTranslations(JsonNode senses, Set<String> translations) {
        if (!senses.isArray()) {
            return;
        }
        for (JsonNode sense : senses) {
            JsonNode currentTranslations = sense.path("translations");
            if (currentTranslations.isArray()) {
                for (JsonNode translation : currentTranslations) {
                    String code = translation.path("language").path("code").asText();
                    String name = translation.path("language").path("name").asText();
                    if (isChineseLanguage(code, name)) {
                        addNormalizedChineseTranslationSegments(translation.path("word").asText(), translations);
                    }
                }
            }
            collectChineseTranslations(sense.path("subsenses"), translations);
        }
    }

    // 将原始翻译文本拆成规范化的中文片段，并做去重。
    private void addNormalizedChineseTranslationSegments(String rawValue, Set<String> translations) {
        if (rawValue == null || rawValue.isBlank()) {
            return;
        }

        String repaired = TextRepairUtils.repair(rawValue).trim();
        if (repaired.isBlank()) {
            return;
        }

        for (String segment : repaired.split("\\s*(?:/|,|;|\\||\uFF0C|\uFF1B)\\s*")) {
            for (String alternative : segment.split("(?i)\\s+or\\s+")) {
                String candidate = sanitizeChineseSegment(alternative);
                if (!candidate.isBlank() && containsHanCharacter(candidate)) {
                    translations.add(UserFacingTextNormalizer.normalizeMeaningText(candidate));
                }
            }
        }
    }

    private String sanitizeChineseSegment(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String candidate = TextRepairUtils.repair(value).trim();
        if (candidate.isBlank()) {
            return "";
        }

        candidate = candidate.replaceAll("\\s*\\([A-Za-z][^)]+\\)", "").trim();
        int firstHanIndex = firstHanIndex(candidate);
        if (firstHanIndex > 0 && hasLatinLetter(candidate.substring(0, firstHanIndex))) {
            candidate = candidate.substring(firstHanIndex).trim();
        }

        int lastHanIndex = lastHanIndex(candidate);
        if (lastHanIndex >= 0 && lastHanIndex + 1 < candidate.length() && hasLatinLetter(candidate.substring(lastHanIndex + 1))) {
            candidate = candidate.substring(0, lastHanIndex + 1).trim();
        }

        candidate = candidate.replaceAll("^[^\\p{IsHan}]+", "").replaceAll("[^\\p{IsHan}A-Za-z0-9]+$", "").trim();
        return UserFacingTextNormalizer.normalizeMeaningText(candidate);
    }

    private int firstHanIndex(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.UnicodeScript.of(value.charAt(index)) == Character.UnicodeScript.HAN) {
                return index;
            }
        }
        return -1;
    }

    private boolean hasLatinLetter(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if ((current >= 'A' && current <= 'Z') || (current >= 'a' && current <= 'z')) {
                return true;
            }
        }
        return false;
    }

    private Long findExistingPublicEntryId(String word) {
        return searchVocabularyMapper.findExistingPublicEntryId(PUBLIC_OWNER_USER_ID, PUBLIC_VISIBILITY, word);
    }

    private Long findPublicWordbookId() {
        return searchWordbookMapper.findPublicWordbookId(
                PUBLIC_OWNER_USER_ID,
                UserFacingTextNormalizer.normalizeDisplayText(PUBLIC_WORDBOOK_NAME)
        );
    }

    // 按需创建共享公共词书。
    private long ensurePublicWordbook() {
        Long existingId = findPublicWordbookId();
        if (existingId != null) {
            searchWordbookMapper.updatePublicWordbookMetadata(
                    existingId,
                    PUBLIC_WORDBOOK_PLATFORM,
                    UserFacingTextNormalizer.normalizeDisplayText(PUBLIC_WORDBOOK_SOURCE),
                    PUBLIC_IMPORT_SOURCE
            );
            return existingId;
        }

        PublicWordbookPo row = new PublicWordbookPo();
        row.setUserId(PUBLIC_OWNER_USER_ID);
        row.setName(UserFacingTextNormalizer.normalizeDisplayText(PUBLIC_WORDBOOK_NAME));
        row.setPlatform(PUBLIC_WORDBOOK_PLATFORM);
        row.setSourceName(UserFacingTextNormalizer.normalizeDisplayText(PUBLIC_WORDBOOK_SOURCE));
        row.setImportSource(PUBLIC_IMPORT_SOURCE);
        searchWordbookMapper.insertPublicWordbook(row);
        if (row.getId() != null) {
            return row.getId();
        }

        Long createdId = findPublicWordbookId();
        if (createdId != null) {
            return createdId;
        }
        throw new IllegalStateException("Failed to create public catalog wordbook");
    }

    private void syncPublicWordbookCount(long wordbookId) {
        searchWordbookMapper.syncWordbookCount(wordbookId);
    }

    private long createPublicEntry(long wordbookId, DictionaryEntryPayload payload) {
        PublicEntryPo row = toPublicEntryPo(wordbookId, payload);
        searchVocabularyMapper.insertPublicEntry(row);
        if (row.getId() != null) {
            return row.getId();
        }

        Long createdId = findPublicEntryId(wordbookId, payload.word());
        if (createdId != null) {
            return createdId;
        }
        throw new IllegalStateException("Failed to create public catalog entry for word: " + payload.word());
    }

    private PublicEntryPo toPublicEntryPo(long wordbookId, DictionaryEntryPayload payload) {
        PublicEntryPo row = new PublicEntryPo();
        row.setUserId(PUBLIC_OWNER_USER_ID);
        row.setWordbookId(wordbookId);
        row.setWord(payload.word());
        row.setPhonetic(resolvePersistedPhonetic(payload.word(), payload.phonetic(), payload.importSource()));
        row.setMeaningCn(payload.meaningCn());
        row.setExampleSentence(payload.exampleSentence());
        row.setCategory(payload.category());
        row.setDefinitionEn(payload.definitionEn());
        row.setTags(payload.tags());
        row.setBncRank(payload.bncRank());
        row.setFrqRank(payload.frqRank());
        row.setWordfreqZipf(payload.wordfreqZipf());
        row.setExchangeInfo(payload.exchangeInfo());
        row.setDataQuality(payload.dataQuality());
        row.setDifficulty(payload.difficulty());
        row.setVisibility(PUBLIC_VISIBILITY);
        row.setAudioUrl(payload.audioUrl());
        row.setImportSource(payload.importSource());
        return row;
    }

    private void updatePublicEntry(long entryId, DictionaryEntryPayload payload) {
        searchVocabularyMapper.updatePublicEntry(
                entryId,
                payload.word(),
                resolvePersistedPhonetic(payload.word(), payload.phonetic(), payload.importSource()),
                payload.meaningCn(),
                payload.exampleSentence(),
                payload.category(),
                payload.definitionEn(),
                payload.tags(),
                payload.bncRank(),
                payload.frqRank(),
                payload.wordfreqZipf(),
                payload.exchangeInfo(),
                payload.dataQuality(),
                payload.difficulty(),
                payload.audioUrl(),
                payload.importSource()
        );
    }

    private void syncPublicEntryToIndex(long entryId, boolean elasticsearchAvailable) {
        if (!elasticsearchAvailable) {
            return;
        }
        try {
            indexPublicEntry(entryId);
        } catch (RuntimeException exception) {
            log.warn("Failed to index public entry {} into Elasticsearch, keeping the database row only: {}", entryId, exception.getMessage());
        }
    }

    private void indexPublicEntry(long entryId) {
        SearchDocumentVo row = searchVocabularyMapper.findDocumentById(entryId);
        if (row != null) {
            indexDocument(row);
        }
    }

    private Long findPublicEntryId(long wordbookId, String word) {
        return searchVocabularyMapper.findPublicEntryId(PUBLIC_OWNER_USER_ID, wordbookId, word);
    }

    private DetailVo loadDetailRow(long entryId) {
        DetailVo row = searchVocabularyMapper.loadDetailRow(entryId);
        if (row == null) {
            throw new NotFoundException("Word not found");
        }
        return row;
    }

    private List<SearchDocumentVo> loadAllRows() {
        return searchVocabularyMapper.loadAllRows();
    }

    // 插入或刷新单个公共词条。
    private ImportOutcome importSingleWord(String word, boolean refreshExisting, boolean elasticsearchAvailable) {
        Long existingId = findExistingPublicEntryId(word);
        if (existingId != null && !refreshExisting) {
            return new ImportOutcome(ImportAction.SKIPPED, existingId, null);
        }

        DictionaryEntryPayload payload = fetchDictionaryEntry(word);
        if (payload == null) {
            return new ImportOutcome(ImportAction.FAILED, existingId, "INCOMPLETE_OR_MISSING_DICTIONARY_PAYLOAD");
        }

        long wordbookId = ensurePublicWordbook();
        if (existingId == null) {
            long entryId = createPublicEntry(wordbookId, payload);
            syncPublicEntryToIndex(entryId, elasticsearchAvailable);
            return new ImportOutcome(ImportAction.IMPORTED, entryId, null);
        }

        updatePublicEntry(existingId, payload);
        syncPublicEntryToIndex(existingId, elasticsearchAvailable);
        return new ImportOutcome(ImportAction.UPDATED, existingId, null);
    }

    private String extractExample(JsonNode senses) {
        if (!senses.isArray()) {
            return "";
        }
        for (JsonNode sense : senses) {
            JsonNode examples = sense.path("examples");
            if (examples.isArray() && !examples.isEmpty()) {
                String value = examples.get(0).asText();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            JsonNode quotes = sense.path("quotes");
            if (quotes.isArray() && !quotes.isEmpty()) {
                String value = quotes.get(0).path("text").asText();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return "";
    }

    private boolean containsHanCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.UnicodeScript.of(value.charAt(index)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private int lastHanIndex(String value) {
        for (int index = value.length() - 1; index >= 0; index--) {
            if (Character.UnicodeScript.of(value.charAt(index)) == Character.UnicodeScript.HAN) {
                return index;
            }
        }
        return -1;
    }

    private boolean isChineseLanguage(String code, String name) {
        String normalizedCode = code == null ? "" : code.toLowerCase(Locale.ROOT);
        String normalizedName = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return normalizedCode.equals("zh")
                || normalizedCode.equals("cmn")
                || normalizedCode.equals("yue")
                || normalizedCode.startsWith("zh-")
                || normalizedName.contains("chinese")
                || normalizedName.contains("mandarin")
                || normalizedName.contains("cantonese");
    }

    private int normalizeDatabaseContent() {
        int updated = 0;
        updated += normalizeVocabularyEntries();
        updated += normalizeWordbooks();
        updated += normalizeImportTasks();
        updated += normalizeStudyFocusAreas();
        return updated;
    }

    // 在重建索引前，先规范化词条文本字段。
    private int normalizeVocabularyEntries() {
        List<VocabularyCleanupVo> rows = searchVocabularyMapper.loadVocabularyCleanupRows();

        int updated = 0;
        for (VocabularyCleanupVo row : rows) {
            String phonetic = resolvePersistedPhonetic(row.getWord(), row.getPhonetic(), row.getImportSource());
            String meaning = UserFacingTextNormalizer.normalizeMeaningText(row.getMeaningCn());
            String example = UserFacingTextNormalizer.normalizeDisplayText(row.getExampleSentence());
            String category = UserFacingTextNormalizer.normalizeMeaningText(row.getCategory());
            String definitionEn = UserFacingTextNormalizer.normalizeDisplayText(row.getDefinitionEn());
            if (!sameText(row.getPhonetic(), phonetic)
                    || !sameText(row.getMeaningCn(), meaning)
                    || !sameText(row.getExampleSentence(), example)
                    || !sameText(row.getCategory(), category)
                    || !sameText(row.getDefinitionEn(), definitionEn)) {
                searchVocabularyMapper.updateVocabularyCleanup(row.getId(), phonetic, meaning, example, category, definitionEn);
                updated++;
            }
        }
        return updated;
    }

    private String resolvePersistedPhonetic(String word, String phonetic, String importSource) {
        String normalized = SearchTextUtools.normalizePhonetic(phonetic);
        if (!PhoneticNormalizer.hasPlaceholder(normalized)) {
            return normalized;
        }
        if (!PUBLIC_IMPORT_SOURCE.equalsIgnoreCase(SearchTextUtools.normalizeImportSource(importSource))) {
            return normalized;
        }
        if (word == null || word.isBlank()) {
            return normalized;
        }
        EcdictCatalogEntry catalogEntry = loadEcdictCatalog().get(normalizeIndexedWord(word));
        if (catalogEntry == null || PhoneticNormalizer.hasPlaceholder(catalogEntry.phonetic())) {
            return normalized;
        }
        return catalogEntry.phonetic();
    }

    // 规范化词书名称和来源名称。
    private int normalizeWordbooks() {
        List<WordbookCleanupVo> rows = searchWordbookMapper.loadWordbookCleanupRows();

        int updated = 0;
        for (WordbookCleanupVo row : rows) {
            String name = UserFacingTextNormalizer.normalizeDisplayText(row.getName());
            String sourceName = UserFacingTextNormalizer.normalizeDisplayText(row.getSourceName());
            if (!sameText(row.getName(), name) || !sameText(row.getSourceName(), sourceName)) {
                searchWordbookMapper.updateWordbookCleanup(row.getId(), name, sourceName);
                updated++;
            }
        }
        return updated;
    }

    // 规范化导入任务里的文本字段。
    private int normalizeImportTasks() {
        List<ImportTaskCleanupVo> rows = searchImportTaskMapper.loadImportTaskCleanupRows();

        int updated = 0;
        for (ImportTaskCleanupVo row : rows) {
            String sourceName = UserFacingTextNormalizer.normalizeDisplayText(row.getSourceName());
            String errorMessage = row.getErrorMessage() == null ? null : UserFacingTextNormalizer.normalizeDisplayText(row.getErrorMessage());
            if (!sameText(row.getSourceName(), sourceName) || !sameNullableText(row.getErrorMessage(), errorMessage)) {
                searchImportTaskMapper.updateImportTaskCleanup(row.getTaskId(), sourceName, errorMessage);
                updated++;
            }
        }
        return updated;
    }

    // 规范化学习重点标签。
    private int normalizeStudyFocusAreas() {
        List<StudyFocusCleanupVo> rows = searchStudyFocusMapper.loadStudyFocusCleanupRows();

        int updated = 0;
        for (StudyFocusCleanupVo row : rows) {
            String focusLabel = UserFacingTextNormalizer.normalizeDisplayText(row.getFocusLabel());
            if (!sameText(row.getFocusLabel(), focusLabel)) {
                searchStudyFocusMapper.updateStudyFocusCleanup(row.getId(), focusLabel);
                updated++;
            }
        }
        return updated;
    }

    private boolean sameText(String raw, String normalized) {
        String left = raw == null ? "" : raw;
        String right = normalized == null ? "" : normalized;
        return left.equals(right);
    }

    private boolean sameNullableText(String raw, String normalized) {
        String left = raw == null ? "" : raw;
        String right = normalized == null ? "" : normalized;
        return left.equals(right);
    }

    private boolean shouldHydrate(String keyword) {
        if (keyword == null) {
            return false;
        }
        String normalized = keyword.trim();
        return !normalized.isBlank()
                && normalized.length() <= 64
                && normalized.matches("[A-Za-z][A-Za-z\\-']*");
    }

    private List<String> defaultSeedWords() {
        return List.of(
                "ability", "access", "account", "achieve", "action", "activity", "adapt", "advance", "advantage", "advice",
                "affect", "approach", "argument", "article", "aspect", "assume", "attempt", "attitude", "audience", "balance",
                "background", "benefit", "challenge", "choice", "comment", "community", "compare", "complete", "concern", "condition",
                "consider", "contact", "context", "contrast", "contribute", "create", "culture", "debate", "decade", "decision",
                "define", "design", "develop", "difference", "difficult", "direction", "discover", "discuss", "effect", "effort",
                "emotion", "encourage", "environment", "evidence", "example", "experience", "feature", "focus", "function", "impact",
                "improve", "include", "increase", "indicate", "individual", "influence", "information", "issue", "knowledge", "language",
                "manage", "method", "notice", "opportunity", "option", "pattern", "perform", "period", "policy", "popular",
                "prepare", "pressure", "process", "project", "protect", "quality", "question", "reason", "reduce", "reflect",
                "relationship", "resource", "respond", "result", "role", "section", "similar", "society", "source", "support"
        ).subList(0, DEFAULT_BATCH_SIZE);
    }

    private String buildSourceLabel(String visibility) {
        return PUBLIC_VISIBILITY.equalsIgnoreCase(visibility) ? PUBLIC_SOURCE_LABEL : PRIVATE_SOURCE_LABEL;
    }

    private boolean tryEnsureIndex() {
        try {
            ensureIndex();
            return true;
        } catch (RuntimeException exception) {
            log.warn("Elasticsearch is unavailable: {}", exception.getMessage());
            return false;
        }
    }

    private void ensureIndex() {
        try {
            createIndex();
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException("Failed to initialize Elasticsearch index", exception);
        }
    }

    private void deleteIndex() throws IOException, InterruptedException {
        sendWithoutBody("DELETE", "/" + INDEX_NAME, true);
    }

    // 创建搜索、建议和详情查询共用的 Elasticsearch 索引。
    private void createIndex() throws IOException, InterruptedException {
        Map<String, Object> mappings = Map.of(
                "settings", Map.of(
                        "analysis", Map.of(
                                "normalizer", Map.of(
                                        "lowercase_keyword", Map.of(
                                                "type", "custom",
                                                "filter", List.of("lowercase")
                                        )
                                )
                        )
                ),
                "mappings", Map.of(
                        "properties", Map.ofEntries(
                                Map.entry("entryId", Map.of("type", "long")),
                                Map.entry("ownerUserId", Map.of("type", "long")),
                                Map.entry("visibility", Map.of("type", "keyword")),
                                Map.entry("wordbookId", Map.of("type", "long")),
                                Map.entry("word", Map.of(
                                        "type", "text",
                                        "fields", Map.of("keyword", Map.of("type", "keyword", "ignore_above", 256))
                                )),
                                Map.entry("wordExact", Map.of(
                                        "type", "keyword",
                                        "normalizer", "lowercase_keyword"
                                )),
                                Map.entry("wordWildcard", Map.of("type", "wildcard")),
                                Map.entry("wordSuggest", Map.of("type", "search_as_you_type")),
                                Map.entry("phonetic", Map.of("type", "text")),
                                Map.entry("meaningCn", Map.of("type", "text")),
                                Map.entry("exampleSentence", Map.of("type", "text")),
                                Map.entry("category", Map.of("type", "text")),
                                Map.entry("definitionEn", Map.of("type", "text")),
                                Map.entry("tags", Map.of("type", "keyword")),
                                Map.entry("bncRank", Map.of("type", "integer")),
                                Map.entry("frqRank", Map.of("type", "integer")),
                                Map.entry("wordfreqZipf", Map.of("type", "float")),
                                Map.entry("dataQuality", Map.of("type", "keyword")),
                                Map.entry("importSource", Map.of("type", "keyword"))
                        )
                )
        );
        sendJson("PUT", "/" + INDEX_NAME, mappings, true);
    }

    // 规范化单条数据后写入 Elasticsearch。
    private void indexDocument(SearchDocumentVo row) {
        try {
            String normalizedWord = TextRepairUtils.repair(row.getWord());
            String normalizedPhonetic = SearchTextUtools.normalizePhonetic(row.getPhonetic());
            String normalizedMeaning = UserFacingTextNormalizer.normalizeMeaningText(row.getMeaningCn());
            String normalizedExample = UserFacingTextNormalizer.normalizeDisplayText(row.getExampleSentence());
            String normalizedCategory = UserFacingTextNormalizer.normalizeMeaningText(row.getCategory());
            String normalizedDefinition = UserFacingTextNormalizer.normalizeDisplayText(row.getDefinitionEn());
            String normalizedTags = UserFacingTextNormalizer.normalizeDisplayText(row.getTags());
            String normalizedDataQuality = UserFacingTextNormalizer.normalizeDisplayText(row.getDataQuality());
            String normalizedImportSource = SearchTextUtools.normalizeImportSource(row.getImportSource());

            Map<String, Object> document = new LinkedHashMap<>();
            document.put("entryId", row.getEntryId());
            document.put("ownerUserId", row.getOwnerUserId());
            document.put("visibility", row.getVisibility());
            document.put("wordbookId", row.getWordbookId());
            document.put("word", normalizedWord);
            document.put("wordExact", normalizeIndexedWord(normalizedWord));
            document.put("wordWildcard", normalizeIndexedWord(normalizedWord));
            document.put("wordSuggest", normalizedWord);
            document.put("phonetic", normalizedPhonetic);
            document.put("meaningCn", normalizedMeaning);
            document.put("exampleSentence", normalizedExample);
            document.put("category", normalizedCategory);
            document.put("definitionEn", normalizedDefinition);
            document.put("tags", normalizedTags);
            document.put("bncRank", row.getBncRank());
            document.put("frqRank", row.getFrqRank());
            document.put("wordfreqZipf", row.getWordfreqZipf());
            document.put("dataQuality", normalizedDataQuality);
            document.put("importSource", normalizedImportSource);
            sendJson("PUT", "/" + INDEX_NAME + "/_doc/" + row.getEntryId(), document, false);
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException("Failed to index Elasticsearch document", exception);
        }
    }

    private JsonNode sendJson(String method, String path, Object body, boolean ignoreBadRequest) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(elasticsearchBaseUrl + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400 && !(ignoreBadRequest && (response.statusCode() == 400 || response.statusCode() == 404))) {
            throw new IllegalStateException("Elasticsearch request failed: " + response.statusCode() + " " + response.body());
        }
        return response.body() == null || response.body().isBlank()
                ? objectMapper.createObjectNode()
                : objectMapper.readTree(response.body());
    }

    private void sendWithoutBody(String method, String path, boolean ignoreNotFound) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(elasticsearchBaseUrl + path))
                .header("Accept", "application/json")
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400 && !(ignoreNotFound && response.statusCode() == 404)) {
            throw new IllegalStateException("Elasticsearch request failed: " + response.statusCode() + " " + response.body());
        }
    }

    private void refreshIndex() {
        try {
            sendWithoutBody("POST", "/" + INDEX_NAME + "/_refresh", false);
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException("Failed to refresh Elasticsearch index", exception);
        }
    }

    private void safeRefreshIndex() {
        try {
            refreshIndex();
        } catch (RuntimeException exception) {
            log.warn("Failed to refresh Elasticsearch index after database update: {}", exception.getMessage());
        }
    }
    private record SearchHitCandidate(
            long entryId,
            String word,
            String phonetic,
            String meaningCn,
            String source,
            String exampleSentence,
            String category,
            String definitionEn,
            String tags,
            Integer bncRank,
            Integer frqRank,
            Double wordfreqZipf,
            String dataQuality,
            String visibility,
            String importSource,
            double score
    ) {
    }

    private record RankedSearchHit(
            long entryId,
            String word,
            String phonetic,
            String meaningCn,
            String source,
            String exampleSentence,
            String category,
            String definitionEn,
            String tags,
            Integer bncRank,
            Integer frqRank,
            Double wordfreqZipf,
            String dataQuality,
            String visibility,
            String importSource,
            double score,
            MatchType matchType
    ) {
    }

    private record DictionaryEntryPayload(
            String word,
            String phonetic,
            String meaningCn,
            String exampleSentence,
            String category,
            String definitionEn,
            String tags,
            Integer bncRank,
            Integer frqRank,
            Double wordfreqZipf,
            String exchangeInfo,
            String dataQuality,
            int difficulty,
            String audioUrl,
            String importSource
    ) {
    }

    private record EcdictCatalogEntry(
            String word,
            String phonetic,
            String meaningCn,
            String category,
            String definitionEn,
            String tags,
            Integer bncRank,
            Integer frqRank,
            Double wordfreqZipf,
            String exchangeInfo,
            String dataQuality,
            String exampleSentence
    ) {
    }

    private record DictionaryApiExtras(
            String exampleSentence,
            String audioUrl
    ) {
    }

    private record SuggestionCandidate(
            long entryId,
            String word,
            String visibility,
            Long ownerUserId,
            double score,
            MatchType matchType
    ) {
    }

    private record SearchScope(
            Long ownerUserId,
            String visibility,
            Long wordbookId,
            boolean allowHydrate
    ) {
    }

    private record ImportOutcome(
            ImportAction action,
            Long entryId,
            String errorMessage
    ) {
    }

    private enum ImportAction {
        IMPORTED,
        UPDATED,
        SKIPPED,
        FAILED
    }

    private enum MatchType {
        EXACT(0, 100),
        PREFIX(1, 85),
        CONTAINS(2, 70),
        TEXT(3, 55);

        private final int rank;
        private final int matchPercent;

        MatchType(int rank, int matchPercent) {
            this.rank = rank;
            this.matchPercent = matchPercent;
        }

        public int rank() {
            return rank;
        }

        public int matchPercent() {
            return matchPercent;
        }
    }
}

