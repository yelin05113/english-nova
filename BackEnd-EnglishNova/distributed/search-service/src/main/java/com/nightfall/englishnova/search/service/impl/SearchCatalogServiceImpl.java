package com.nightfall.englishnova.search.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nightfall.englishnova.search.config.ExampleEnrichmentProperties;
import com.nightfall.englishnova.search.config.PublicCatalogSourceProperties;
import com.nightfall.englishnova.search.domain.po.PublicEntryPo;
import com.nightfall.englishnova.search.domain.po.PublicCatalogImportJobPo;
import com.nightfall.englishnova.search.domain.vo.DetailVo;
import com.nightfall.englishnova.search.domain.vo.ExampleEnrichmentTaskVo;
import com.nightfall.englishnova.search.domain.vo.PublicCatalogImportItemVo;
import com.nightfall.englishnova.search.domain.vo.PublicCatalogImportJobVo;
import com.nightfall.englishnova.search.domain.vo.PublicWordbookRow;
import com.nightfall.englishnova.search.domain.vo.SearchDocumentVo;
import com.nightfall.englishnova.search.domain.vo.VocabularyCleanupVo;
import com.nightfall.englishnova.search.domain.vo.WordbookCleanupVo;
import com.nightfall.englishnova.search.mapper.ExampleEnrichmentTaskMapper;
import com.nightfall.englishnova.search.mapper.PublicCatalogImportJobMapper;
import com.nightfall.englishnova.search.mapper.SearchVocabularyMapper;
import com.nightfall.englishnova.search.mapper.SearchWordbookMapper;
import com.nightfall.englishnova.search.service.AudioProxyPayload;
import com.nightfall.englishnova.search.service.ExampleAudioStorageService;
import com.nightfall.englishnova.search.service.OpenAiExampleEnrichmentClient;
import com.nightfall.englishnova.search.service.SearchCatalogService;
import com.nightfall.englishnova.search.utools.SearchTextUtools;
import com.nightfall.englishnova.shared.auth.CurrentUser;
import com.nightfall.englishnova.shared.dto.PublicCatalogImportJobDto;
import com.nightfall.englishnova.shared.dto.PublicCatalogImportJobRequest;
import com.nightfall.englishnova.shared.dto.PublicCatalogImportRequest;
import com.nightfall.englishnova.shared.dto.PublicCatalogImportResultDto;
import com.nightfall.englishnova.shared.dto.PublicWordbookDto;
import com.nightfall.englishnova.shared.dto.PublicWordbookEntryDto;
import com.nightfall.englishnova.shared.dto.SearchHitDto;
import com.nightfall.englishnova.shared.dto.SearchSuggestionDto;
import com.nightfall.englishnova.shared.dto.UpdatePublicWordbookDailyTargetRequest;
import com.nightfall.englishnova.shared.dto.WordDetailDto;
import com.nightfall.englishnova.shared.dto.WordSearchResponseDto;
import com.nightfall.englishnova.shared.enums.VocabularyEntryType;
import com.nightfall.englishnova.shared.events.WordbookImportedEvent;
import com.nightfall.englishnova.shared.exception.ConflictException;
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
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * 鍩轰簬 Elasticsearch 鐨勬悳绱㈢洰褰曟湇鍔°€? * 璐熻矗鍏叡璇嶅簱涓庣鏈夎瘝搴撴悳绱€佽瘝鏉¤鎯呮煡璇紝浠ュ強鍏叡璇嶅簱琛ュ叏瀵煎叆銆? */
@Service
public class SearchCatalogServiceImpl implements SearchCatalogService {

    private static final Logger log = LoggerFactory.getLogger(SearchCatalogServiceImpl.class);

    private static final String INDEX_NAME = "english-nova-words";
    private static final String PUBLIC_ENTRY_TYPE = "PUBLIC";
    private static final String USER_ENTRY_TYPE = "USER";
    private static final String PUBLIC_VISIBILITY = "PUBLIC";
    private static final String PRIVATE_VISIBILITY = "PRIVATE";
    private static final String PUBLIC_SOURCE_LABEL = "Public Catalog - ECDICT";
    private static final String PRIVATE_SOURCE_LABEL = "My Wordbook";
    private static final String PUBLIC_IMPORT_SOURCE = "ecdict";
    private static final String ECDICT_HIGH_FREQUENCY_RESOURCE = "public-catalog/ecdict-high-frequency-10000.tsv";
    private static final String ECDICT_HIGH_FREQUENCY_5000_RESOURCE = "public-catalog/ecdict-high-frequency-5000.tsv";
    private static final String HIGH_FREQUENCY_SOURCE_NAME = "ecdict-high-frequency-10000";
    private static final String HIGH_FREQUENCY_5000_SOURCE_NAME = "ecdict-high-frequency-5000";
    private static final String LEGACY_HIGH_FREQUENCY_5000_SOURCE_NAME = "high-frequency-5000";
    private static final String EXTERNAL_CATALOG_MANIFEST_FILE = "manifest.json";
    private static final String JOB_STATUS_PENDING = "PENDING";
    private static final String JOB_STATUS_RUNNING = "RUNNING";
    private static final String JOB_STATUS_CANCELLED = "CANCELLED";
    private static final String ITEM_STATUS_IMPORTED = "IMPORTED";
    private static final String ITEM_STATUS_UPDATED = "UPDATED";
    private static final int MAX_IMPORT_WORDS = 500;
    private static final int MAX_HIGH_FREQUENCY_WORDS = 10000;
    private static final int DEFAULT_HIGH_FREQUENCY_LIMIT = 10000;
    private static final int DEFAULT_HIGH_FREQUENCY_BATCH_SIZE = 150;
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int DEFAULT_PUBLIC_AUDIO_BACKFILL_BATCH_SIZE = 500;
    private static final int DEFAULT_ENRICHMENT_BACKFILL_BATCH_SIZE = 200;
    private static final int ENRICHMENT_RUNNING_TIMEOUT_MINUTES = 30;
    private static final String EXAMPLE_AUDIO_CONTENT_TYPE = "audio/mpeg";
    private static final int SEARCH_RESULT_SIZE = 18;
    private static final int SEARCH_RESULT_FETCH_SIZE = 60;
    private static final int SUGGESTION_FETCH_SIZE = 40;
    private static final int SUGGESTION_LIMIT = 10;
    private static final String FREE_DICTIONARY_API_BASE_URL = "https://freedictionaryapi.com/api/v1";
    private static final String AUDIO_API_BASE_URL = "https://api.dictionaryapi.dev/api/v2/entries/en";
    private static final String AUDIO_FALLBACK_BASE_URL = "https://dict.youdao.com/dictvoice?type=2&audio=";
    private static final Pattern PART_OF_SPEECH_PREFIX_PATTERN = Pattern.compile(
            "^(?:n|v|vt|vi|adj|adv|prep|pron|conj|art|aux|num|int|det|abbr|phr|pref|suf|modal)\\.(?:\\s|$)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ENGLISH_TOKEN_PATTERN = Pattern.compile("[A-Za-z]+(?:['-][A-Za-z]+)?");
    private static final Set<String> AUDIO_PROXY_ALLOWED_HOSTS = Set.of(
            "api.dictionaryapi.dev",
            "dict.youdao.com",
            "translate.google.com"
    );

    private final SearchVocabularyMapper searchVocabularyMapper;
    private final SearchWordbookMapper searchWordbookMapper;
    private final ExampleEnrichmentTaskMapper exampleEnrichmentTaskMapper;
    private final PublicCatalogImportJobMapper publicCatalogImportJobMapper;
    private final ExampleEnrichmentProperties exampleEnrichmentProperties;
    private final PublicCatalogSourceProperties publicCatalogSourceProperties;
    private final OpenAiExampleEnrichmentClient openAiExampleEnrichmentClient;
    private final ExampleAudioStorageService exampleAudioStorageService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String elasticsearchBaseUrl;
    private final int publicCatalogImportConcurrency;
    private final AtomicBoolean importWorkerRunning = new AtomicBoolean(false);
    private final AtomicBoolean publicAudioBackfillRunning = new AtomicBoolean(false);
    private final AtomicBoolean exampleEnrichmentWorkerRunning = new AtomicBoolean(false);
    private final AtomicBoolean exampleEnrichmentBackfillRunning = new AtomicBoolean(false);
    private volatile CatalogRegistry catalogRegistryCache;

    public SearchCatalogServiceImpl(
            SearchVocabularyMapper searchVocabularyMapper,
            SearchWordbookMapper searchWordbookMapper,
            ExampleEnrichmentTaskMapper exampleEnrichmentTaskMapper,
            PublicCatalogImportJobMapper publicCatalogImportJobMapper,
            ExampleEnrichmentProperties exampleEnrichmentProperties,
            PublicCatalogSourceProperties publicCatalogSourceProperties,
            OpenAiExampleEnrichmentClient openAiExampleEnrichmentClient,
            ExampleAudioStorageService exampleAudioStorageService,
            ObjectMapper objectMapper,
            @Value("${spring.elasticsearch.uris}") String elasticsearchBaseUrl,
            @Value("${english-nova.search.public-catalog-import-concurrency:4}") int publicCatalogImportConcurrency
    ) {
        this.searchVocabularyMapper = searchVocabularyMapper;
        this.searchWordbookMapper = searchWordbookMapper;
        this.exampleEnrichmentTaskMapper = exampleEnrichmentTaskMapper;
        this.publicCatalogImportJobMapper = publicCatalogImportJobMapper;
        this.exampleEnrichmentProperties = exampleEnrichmentProperties;
        this.publicCatalogSourceProperties = publicCatalogSourceProperties;
        this.openAiExampleEnrichmentClient = openAiExampleEnrichmentClient;
        this.exampleAudioStorageService = exampleAudioStorageService;
        this.objectMapper = objectMapper;
        this.elasticsearchBaseUrl = elasticsearchBaseUrl.endsWith("/")
                ? elasticsearchBaseUrl.substring(0, elasticsearchBaseUrl.length() - 1)
                : elasticsearchBaseUrl;
        this.httpClient = HttpClient.newHttpClient();
        this.publicCatalogImportConcurrency = Math.max(1, publicCatalogImportConcurrency);
    }

    /**
     * 鏈寚瀹氳瘝涔︽椂鍙悳绱㈠叕鍏辫瘝搴擄紱鎸囧畾璇嶄功鏃跺彧鎼滅储褰撳墠鐢ㄦ埛鎷ユ湁鐨勮璇嶄功銆?     */
    public WordSearchResponseDto searchVocabulary(String keyword, CurrentUser user, Long wordbookId) {
        String normalizedKeyword = SearchTextUtools.normalizeSearchKeyword(keyword);
        if (normalizedKeyword.isBlank()) {
            return new WordSearchResponseDto(List.of());
        }

        SearchScope scope = resolveSearchScope(user, wordbookId);
        ensureIndex();
        List<SearchHitDto> hits = searchByScope(normalizedKeyword, scope.entryType(), scope.ownerUserId(), scope.visibility(), scope.wordbookId());
        if (hits.isEmpty() && scope.allowHydrate() && shouldHydrate(normalizedKeyword)) {
            importWords(List.of(normalizedKeyword), false);
            hits = searchByScope(normalizedKeyword, scope.entryType(), scope.ownerUserId(), scope.visibility(), scope.wordbookId());
        }
        return new WordSearchResponseDto(hits);
    }

    /**
     * 褰撳叧閿瓧鐪嬭捣鏉ュ儚鍗曡瘝鏌ヨ鏃讹紝杩斿洖鎼滅储寤鸿銆?     */
    public List<SearchSuggestionDto> searchSuggestions(String keyword, CurrentUser user, Long wordbookId) {
        String normalizedKeyword = SearchTextUtools.normalizeSearchKeyword(keyword);
        if (normalizedKeyword.isBlank() || !shouldHydrate(normalizedKeyword)) {
            return List.of();
        }

        SearchScope scope = resolveSearchScope(user, wordbookId);
        ensureIndex();
        return searchSuggestionsByWordMatch(normalizedKeyword, scope.entryType(), scope.ownerUserId(), scope.visibility(), scope.wordbookId());
    }

    /**
     * 鍔犺浇鍗曚釜璇嶆潯璇︽儏锛屽苟鍦ㄦ潯浠舵弧瓒虫椂鎳掑姞杞借ˉ鍏ㄩ煶棰戝湴鍧€銆?     */
    public WordDetailDto getWordDetail(long entryId, VocabularyEntryType entryType, CurrentUser user) {
        DetailVo row = loadDetailRow(entryId, entryType);
        if (PRIVATE_VISIBILITY.equalsIgnoreCase(row.getVisibility())
                && (user == null || row.getOwnerUserId() == null || row.getOwnerUserId() != user.id())) {
            throw new ForbiddenException("You cannot access this word");
        }

        String normalizedWord = TextRepairUtils.repair(row.getWord());
        String audioUrl = SearchTextUtools.normalizeAudioUrl(row.getAudioUrl());
        if (audioUrl.isBlank() && shouldHydrate(normalizedWord)) {
            audioUrl = SearchTextUtools.normalizeAudioUrl(fetchAudioUrl(normalizedWord));
        }
        if (entryType == VocabularyEntryType.PUBLIC && !audioUrl.isBlank() && !sameText(row.getAudioUrl(), audioUrl)) {
            searchVocabularyMapper.updatePublicAudioUrl(entryId, audioUrl);
        }
        String clientAudioUrl = toClientAudioUrl(audioUrl);

        String displayExampleSentence = resolveDisplayExampleSentence(
                row.getEntryType(),
                row.getExampleSentence(),
                row.getCorrectedEnglish()
        );

        return new WordDetailDto(
                row.getEntryId(),
                row.getEntryType(),
                row.getOwnerUserId(),
                row.getWordbookId(),
                UserFacingTextNormalizer.normalizeDisplayText(row.getWordbookName()),
                normalizedWord,
                SearchTextUtools.normalizePhonetic(row.getPhonetic()),
                UserFacingTextNormalizer.normalizeMeaningText(row.getMeaningCn()),
                displayExampleSentence,
                UserFacingTextNormalizer.normalizeDisplayText(row.getCorrectedEnglish()),
                UserFacingTextNormalizer.normalizeDisplayText(row.getChineseSentence()),
                normalizeExampleAudioUrl(entryType.name(), row.getExampleAudioUrl()),
                UserFacingTextNormalizer.normalizeMeaningText(row.getCategory()),
                row.getBncRank(),
                row.getFrqRank(),
                row.getWordfreqZipf(),
                UserFacingTextNormalizer.normalizeDisplayText(row.getExchangeInfo()),
                UserFacingTextNormalizer.normalizeDisplayText(row.getDataQuality()),
                row.getDifficulty(),
                row.getVisibility(),
                buildSourceLabel(row.getEntryType()),
                UserFacingTextNormalizer.normalizeDisplayText(row.getSourceName()),
                SearchTextUtools.normalizeImportSource(row.getImportSource()),
                clientAudioUrl
        );
    }

    @Override
    public AudioProxyPayload getAudioProxy(String sourceUrl) {
        URI uri;
        try {
            uri = URI.create(sourceUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid audio source url", exception);
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(scheme) || host == null || !AUDIO_PROXY_ALLOWED_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unsupported audio source");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Accept", "audio/*,*/*;q=0.8")
                    .header("User-Agent", "EnglishNova/1.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400 || response.body() == null || response.body().length == 0) {
                throw new IllegalStateException("Failed to fetch audio source");
            }
            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse("audio/mpeg");
            return new AudioProxyPayload(response.body(), contentType);
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException("Failed to proxy audio source", exception);
        }
    }

    @Override
    public AudioProxyPayload getExampleAudio(long entryId) {
        SearchDocumentVo row = searchVocabularyMapper.findPublicDocumentById(entryId);
        if (row == null) {
            throw new NotFoundException("Generated example audio not found");
        }
        String correctedEnglish = UserFacingTextNormalizer.normalizeDisplayText(row.getCorrectedEnglish());
        if (!hasCleanText(row.getExampleAudioUrl()) || !hasCleanText(correctedEnglish)) {
            throw new NotFoundException("Generated example audio not found");
        }
        if (!exampleAudioStorageService.hasPublicExampleAudio(entryId, correctedEnglish)) {
            exampleEnrichmentTaskMapper.upsertTask(PUBLIC_ENTRY_TYPE, entryId);
            throw new NotFoundException("Generated example audio not found");
        }
        return new AudioProxyPayload(
                exampleAudioStorageService.loadPublicExampleAudio(entryId, correctedEnglish),
                EXAMPLE_AUDIO_CONTENT_TYPE
        );
    }

    public List<PublicWordbookDto> listPublicWordbooks(CurrentUser user) {
        long userId = user == null ? -1L : user.id();
        return searchWordbookMapper.listPublicWordbooks(userId).stream()
                .map(row -> new PublicWordbookDto(
                        row.getId(),
                        UserFacingTextNormalizer.normalizeDisplayText(row.getName()),
                        UserFacingTextNormalizer.normalizeDisplayText(row.getSourceName()),
                        row.getSourceUrl(),
                        UserFacingTextNormalizer.normalizeDisplayText(row.getLicenseName()),
                        row.getLicenseUrl(),
                        row.getTag(),
                        row.getWordCount(),
                        row.isSubscribed(),
                        row.getCompletedCount(),
                        row.getWrongCount(),
                        row.getDailyTargetCount(),
                        row.getTodayCompletedCount(),
                        row.getTodayCorrectAttempts(),
                        row.getTodayTotalAttempts(),
                        row.getNextSortOrder(),
                        row.getCreatedAt().toInstant().atOffset(ZoneOffset.UTC),
                        row.getUpdatedAt().toInstant().atOffset(ZoneOffset.UTC)
                ))
                .toList();
    }

    public List<PublicWordbookEntryDto> listPublicWordbookEntries(long publicWordbookId) {
        requirePublicWordbook(publicWordbookId);
        return searchWordbookMapper.listPublicWordbookEntries(publicWordbookId).stream()
                .map(row -> {
                    String correctedExampleSentence = UserFacingTextNormalizer.normalizeDisplayText(row.getCorrectedEnglish());
                    return new PublicWordbookEntryDto(
                            row.getPublicEntryId(),
                            row.getSortOrder(),
                            TextRepairUtils.repair(row.getWord()),
                            SearchTextUtools.normalizePhonetic(row.getPhonetic()),
                            UserFacingTextNormalizer.normalizeMeaningText(row.getMeaningCn()),
                            resolveDisplayExampleSentence(PUBLIC_ENTRY_TYPE, row.getExampleSentence(), row.getCorrectedEnglish()),
                            correctedExampleSentence,
                            UserFacingTextNormalizer.normalizeDisplayText(row.getChineseSentence()),
                            normalizeExampleAudioUrl(PUBLIC_ENTRY_TYPE, row.getExampleAudioUrl()),
                            row.getBncRank(),
                            row.getFrqRank(),
                            row.getWordfreqZipf()
                    );
                })
                .toList();
    }

    @Transactional
    public PublicWordbookDto subscribePublicWordbook(long publicWordbookId, CurrentUser user) {
        PublicWordbookRow publicWordbook = requirePublicWordbook(publicWordbookId);
        if (publicWordbook.getWordCount() <= 0) {
            throw new IllegalArgumentException("Public wordbook has no entries");
        }
        if (searchWordbookMapper.countUserPublicWordbook(user.id(), publicWordbookId) > 0) {
            throw new ConflictException("You have already subscribed to this public wordbook");
        }
        searchWordbookMapper.insertUserPublicWordbook(user.id(), publicWordbookId);
        return requireUserPublicWordbook(user.id(), publicWordbookId);
    }

    @Transactional
    public PublicWordbookDto unsubscribePublicWordbook(long publicWordbookId, CurrentUser user) {
        requirePublicWordbook(publicWordbookId);
        int deleted = searchWordbookMapper.deleteUserPublicWordbook(user.id(), publicWordbookId);
        if (deleted == 0) {
            throw new NotFoundException("Public wordbook subscription not found");
        }
        searchWordbookMapper.deleteUserPublicWordbookWrongEntries(user.id(), publicWordbookId);
        searchWordbookMapper.cancelActivePublicQuizSessions(user.id(), publicWordbookId);
        return requireUserPublicWordbook(user.id(), publicWordbookId);
    }

    @Transactional
    public PublicWordbookDto resetPublicWordbookProgress(long publicWordbookId, CurrentUser user) {
        requirePublicWordbook(publicWordbookId);
        int updated = searchWordbookMapper.resetUserPublicWordbook(user.id(), publicWordbookId);
        if (updated == 0) {
            throw new NotFoundException("Public wordbook subscription not found");
        }
        searchWordbookMapper.deleteUserPublicWordbookWrongEntries(user.id(), publicWordbookId);
        searchWordbookMapper.cancelActivePublicQuizSessions(user.id(), publicWordbookId);
        return requireUserPublicWordbook(user.id(), publicWordbookId);
    }

    @Transactional
    public PublicWordbookDto updatePublicWordbookDailyTarget(
            long publicWordbookId,
            UpdatePublicWordbookDailyTargetRequest request,
            CurrentUser user
    ) {
        PublicWordbookRow publicWordbook = requirePublicWordbook(publicWordbookId);
        int dailyTargetCount = request.dailyTargetCount() == null ? 0 : request.dailyTargetCount();
        int maxAllowedTarget = Math.min(1000, Math.max(publicWordbook.getWordCount(), 0));
        if (dailyTargetCount > maxAllowedTarget) {
            throw new IllegalArgumentException("每日背词数量超出当前词书可选范围");
        }
        int updated = searchWordbookMapper.updateUserPublicWordbookDailyTarget(user.id(), publicWordbookId, dailyTargetCount);
        if (updated == 0) {
            throw new NotFoundException("Public wordbook subscription not found");
        }
        searchWordbookMapper.cancelActivePublicQuizSessions(user.id(), publicWordbookId);
        return requireUserPublicWordbook(user.id(), publicWordbookId);
    }

    private PublicWordbookRow requirePublicWordbook(long publicWordbookId) {
        PublicWordbookRow row = searchWordbookMapper.findPublicWordbook(publicWordbookId);
        if (row == null) {
            throw new NotFoundException("Public wordbook not found");
        }
        return row;
    }

    private PublicWordbookDto requireUserPublicWordbook(long userId, long publicWordbookId) {
        PublicWordbookRow row = searchWordbookMapper.findUserPublicWordbook(userId, publicWordbookId);
        if (row == null) {
            throw new NotFoundException("Public wordbook not found");
        }
        return new PublicWordbookDto(
                row.getId(),
                UserFacingTextNormalizer.normalizeDisplayText(row.getName()),
                UserFacingTextNormalizer.normalizeDisplayText(row.getSourceName()),
                row.getSourceUrl(),
                UserFacingTextNormalizer.normalizeDisplayText(row.getLicenseName()),
                row.getLicenseUrl(),
                row.getTag(),
                row.getWordCount(),
                row.isSubscribed(),
                row.getCompletedCount(),
                row.getWrongCount(),
                row.getDailyTargetCount(),
                row.getTodayCompletedCount(),
                row.getTodayCorrectAttempts(),
                row.getTodayTotalAttempts(),
                row.getNextSortOrder(),
                row.getCreatedAt().toInstant().atOffset(ZoneOffset.UTC),
                row.getUpdatedAt().toInstant().atOffset(ZoneOffset.UTC)
        );
    }

    /**
     * 灏嗗崟璇嶅鍏ュ叡浜叕鍏辫瘝搴撱€?     */
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
        String resolvedSourceName = resolveHighFrequencySourceName(request == null ? null : request.sourceName());

        List<String> words = loadHighFrequencyWords(resolvedSourceName);
        if (words.size() > resolvedLimit) {
            words = words.subList(0, resolvedLimit);
        }

        PublicCatalogImportJobPo job = new PublicCatalogImportJobPo(
                null,
                resolvedSourceName,
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
            while (true) {
                PublicCatalogImportJobVo job = publicCatalogImportJobMapper.findNextRunnableJob();
                if (job == null) {
                    return;
                }
                boolean processedBatch = processPublicCatalogImportJob(job);
                if (!processedBatch) {
                    return;
                }
            }
        } finally {
            importWorkerRunning.set(false);
        }
    }

    @Scheduled(fixedDelayString = "${english-nova.search.public-audio-backfill-delay-ms:4000}")
    public void backfillMissingPublicAudio() {
        if (!publicAudioBackfillRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            int batchSize = DEFAULT_PUBLIC_AUDIO_BACKFILL_BATCH_SIZE;
            List<VocabularyCleanupVo> rows = searchVocabularyMapper.loadPublicMissingAudioRows(batchSize);
            if (rows.isEmpty()) {
                return;
            }

            int updated = 0;
            for (VocabularyCleanupVo row : rows) {
                String audioUrl = resolvePersistedAudioUrl(row.getWord(), row.getAudioUrl(), row.getImportSource());
                if (!sameText(row.getAudioUrl(), audioUrl) && hasCleanText(audioUrl)) {
                    searchVocabularyMapper.updatePublicAudioUrl(row.getId(), audioUrl);
                    updated++;
                }
            }

            if (updated > 0) {
                int remaining = searchVocabularyMapper.countPublicMissingAudioRows();
                log.info("Backfilled audio for {} public catalog entries in this batch, {} entries still missing audio", updated, remaining);
            }
        } finally {
            publicAudioBackfillRunning.set(false);
        }
    }

    @Scheduled(fixedDelayString = "${english-nova.enrichment.worker-delay-ms:5000}")
    public void processExampleEnrichmentTasks() {
        if (!exampleEnrichmentWorkerRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            if (!openAiExampleEnrichmentClient.isTextConfigured()) {
                return;
            }

            int batchSize = exampleEnrichmentProperties.resolvedBatchSize();
            int workerConcurrency = exampleEnrichmentProperties.resolvedWorkerConcurrency();
            int claimLimit = Math.max(batchSize, batchSize * workerConcurrency);
            int maxRetries = exampleEnrichmentProperties.resolvedMaxRetries();
            exampleEnrichmentTaskMapper.resetTimedOutRunningTasks(ENRICHMENT_RUNNING_TIMEOUT_MINUTES);
            List<ExampleEnrichmentTaskVo> candidates = exampleEnrichmentTaskMapper.findPendingTasks(
                    claimLimit,
                    maxRetries
            );
            if (candidates.isEmpty()) {
                return;
            }

            List<ClaimedEnrichmentTask> claimedTasks = new ArrayList<>();
            List<PublicTaskContext> publicTextTasks = new ArrayList<>();
            List<PublicTaskContext> publicAudioOnlyTasks = new ArrayList<>();
            List<UserTaskContext> userTextTasks = new ArrayList<>();
            for (ExampleEnrichmentTaskVo task : candidates) {
                if (exampleEnrichmentTaskMapper.markTaskRunning(task.getId(), maxRetries) == 0) {
                    continue;
                }

                SearchDocumentVo entry = loadSearchDocument(task.getEntryType(), task.getEntryId());
                if (entry == null) {
                    exampleEnrichmentTaskMapper.markTaskSkipped(task.getId(), "ENTRY_NOT_FOUND");
                    continue;
                }
                ClaimedEnrichmentTask claimedTask = new ClaimedEnrichmentTask(task.getId(), task.getEntryType(), task.getEntryId());
                claimedTasks.add(claimedTask);
                if (PUBLIC_ENTRY_TYPE.equalsIgnoreCase(task.getEntryType())) {
                    PublicTaskContext context = new PublicTaskContext(claimedTask, entry);
                    if (needsPublicExampleText(entry)) {
                        publicTextTasks.add(context);
                    } else if (needsPublicExampleAudio(entry)) {
                        publicAudioOnlyTasks.add(context);
                    } else {
                        exampleEnrichmentTaskMapper.markTaskSucceeded(task.getId());
                    }
                    continue;
                }

                String originalEnglish = UserFacingTextNormalizer.normalizeDisplayText(entry.getExampleSentence());
                if (!shouldEnrichExampleSentence(originalEnglish)) {
                    exampleEnrichmentTaskMapper.markTaskSkipped(task.getId(), "SKIPPED_NON_ENGLISH_EXAMPLE");
                    continue;
                }
                userTextTasks.add(new UserTaskContext(claimedTask, entry, originalEnglish));
            }

            Map<String, PublicEnrichmentOutcome> publicOutcomeByKey = processPublicTextTasks(
                    publicTextTasks,
                    batchSize,
                    workerConcurrency
            );
            Map<String, UserEnrichmentOutcome> userOutcomeByKey = processUserTextTasks(
                    userTextTasks,
                    batchSize,
                    workerConcurrency
            );

            boolean elasticsearchAvailable = tryEnsureIndex();
            boolean indexChanged = false;

            indexChanged = processPublicTaskCompletions(
                    publicTextTasks,
                    publicAudioOnlyTasks,
                    publicOutcomeByKey,
                    elasticsearchAvailable
            ) || indexChanged;

            for (UserTaskContext context : userTextTasks) {
                String taskKey = entryTaskKey(context.task().entryType(), context.task().entryId());
                UserEnrichmentOutcome outcome = userOutcomeByKey.get(taskKey);
                if (outcome == null) {
                    exampleEnrichmentTaskMapper.markTaskFailed(context.task().taskId(), "OPENAI_RESULT_MISSING");
                    continue;
                }
                if (!outcome.success()) {
                    exampleEnrichmentTaskMapper.markTaskFailed(context.task().taskId(), outcome.errorMessage());
                    continue;
                }
                updateExampleEnrichment(context.task().entryType(), context.task().entryId(), outcome.correctedEnglish(), outcome.chineseSentence(), "");
                exampleEnrichmentTaskMapper.markTaskSucceeded(context.task().taskId());
                syncEntryToIndex(context.task().entryType(), context.task().entryId(), elasticsearchAvailable);
                indexChanged = indexChanged || elasticsearchAvailable;
            }

            if (indexChanged) {
                safeRefreshIndex();
            }
        } finally {
            exampleEnrichmentWorkerRunning.set(false);
        }
    }

    @Scheduled(fixedDelayString = "${english-nova.enrichment.backfill-delay-ms:30000}")
    public void backfillExampleEnrichmentTasks() {
        if (!exampleEnrichmentBackfillRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            int inserted = 0;
            int publicLimit = exampleEnrichmentProperties.resolvedPublicLimit();
            inserted += exampleEnrichmentTaskMapper.skipOutOfScopePublicTasks(publicLimit);
            inserted += exampleEnrichmentTaskMapper.resetPublicIncompleteTasks(DEFAULT_ENRICHMENT_BACKFILL_BATCH_SIZE, publicLimit);
            inserted += exampleEnrichmentTaskMapper.backfillPublicTasks(DEFAULT_ENRICHMENT_BACKFILL_BATCH_SIZE, publicLimit);
            inserted += exampleEnrichmentTaskMapper.backfillUserTasks(DEFAULT_ENRICHMENT_BACKFILL_BATCH_SIZE);
            if (inserted > 0) {
                log.info("Backfilled {} example enrichment tasks", inserted);
            }
        } finally {
            exampleEnrichmentBackfillRunning.set(false);
        }
    }

    private boolean processPublicCatalogImportJob(PublicCatalogImportJobVo job) {
        publicCatalogImportJobMapper.startJob(job.getId());
        PublicCatalogImportJobVo currentJob = publicCatalogImportJobMapper.findJob(job.getId());
        if (currentJob == null) {
            return false;
        }
        publicCatalogImportJobMapper.resetRunningItems(currentJob.getId());

        List<PublicCatalogImportItemVo> items = publicCatalogImportJobMapper.claimPendingItems(
                currentJob.getId(),
                Math.max(1, Math.min(currentJob.getBatchSize(), MAX_IMPORT_WORDS))
        );
        if (items.isEmpty()) {
            publicCatalogImportJobMapper.refreshJobCounters(currentJob.getId());
            publicCatalogImportJobMapper.completeJobIfFinished(currentJob.getId());
            return false;
        }

        boolean elasticsearchAvailable = tryEnsureIndex();
        boolean databaseChanged = false;
        boolean indexChanged = false;
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(publicCatalogImportConcurrency, items.size()));
        try {
            List<Future<ImportTaskResult>> futures = new ArrayList<>(items.size());
            for (PublicCatalogImportItemVo item : items) {
                futures.add(executor.submit(() -> processPublicCatalogImportItem(item, currentJob, elasticsearchAvailable)));
            }

            for (Future<ImportTaskResult> future : futures) {
                ImportTaskResult result = future.get();
                if (!result.claimed()) {
                    continue;
                }
                switch (result.outcome().action()) {
                    case IMPORTED -> {
                        publicCatalogImportJobMapper.markItemImported(
                                result.itemId(),
                                result.outcome().entryId(),
                                ITEM_STATUS_IMPORTED,
                                result.outcome().hasExample()
                        );
                        databaseChanged = true;
                        indexChanged = true;
                    }
                    case UPDATED -> {
                        publicCatalogImportJobMapper.markItemImported(
                                result.itemId(),
                                result.outcome().entryId(),
                                ITEM_STATUS_UPDATED,
                                result.outcome().hasExample()
                        );
                        databaseChanged = true;
                        indexChanged = true;
                    }
                    case SKIPPED -> publicCatalogImportJobMapper.markItemSkipped(result.itemId(), result.outcome().entryId());
                    case FAILED -> publicCatalogImportJobMapper.markItemFailed(result.itemId(), result.outcome().errorMessage());
                }
            }

            if (indexChanged && elasticsearchAvailable) {
                safeRefreshIndex();
            }
            publicCatalogImportJobMapper.refreshJobCounters(currentJob.getId());
            publicCatalogImportJobMapper.completeJobIfFinished(currentJob.getId());
            return databaseChanged || indexChanged || !items.isEmpty();
        } catch (Exception exception) {
            publicCatalogImportJobMapper.failJob(currentJob.getId(), normalizeErrorMessage(exception));
            return false;
        } finally {
            executor.shutdown();
        }
    }

    private ImportTaskResult processPublicCatalogImportItem(
            PublicCatalogImportItemVo item,
            PublicCatalogImportJobVo currentJob,
            boolean elasticsearchAvailable
    ) {
        if (publicCatalogImportJobMapper.markItemRunning(item.getId()) == 0) {
            return new ImportTaskResult(item.getId(), false, new ImportOutcome(ImportAction.SKIPPED, null, null, false));
        }
        try {
            ImportOutcome outcome = importSingleWord(
                    item.getWord(),
                    currentJob.isRefreshExisting(),
                    elasticsearchAvailable,
                    currentJob.getSourceName()
            );
            return new ImportTaskResult(item.getId(), true, outcome);
        } catch (Exception exception) {
            return new ImportTaskResult(
                    item.getId(),
                    true,
                    new ImportOutcome(ImportAction.FAILED, null, normalizeErrorMessage(exception), false)
            );
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
     * 搴旂敤鍚姩鍚庨噸寤烘暣濂楁悳绱㈢储寮曘€?     */
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
     * 鍦ㄨ瘝涔﹀鍏ュ畬鎴愬悗锛屽皢鏁版嵁鍚屾鍒?Elasticsearch銆?     */
    @RabbitListener(queues = "${english-nova.search.index-queue}")
    public void handleImportedWordbook(WordbookImportedEvent event) {
        exampleEnrichmentTaskMapper.insertTasksForUserWordbook(event.userId(), event.wordbookId());
        if (!tryEnsureIndex()) {
            log.warn("Skipping Elasticsearch sync for imported wordbook {} because the cluster is unavailable", event.wordbookId());
            return;
        }
        searchVocabularyMapper.listUserByWordbook(event.userId(), event.wordbookId()).forEach(this::indexDocument);
        safeRefreshIndex();
    }

        private List<SearchHitDto> searchByScope(String keyword, String entryType, Long ownerUserId, String visibility, Long wordbookId) {
        try {
            String normalizedWordKeyword = normalizeIndexedWord(keyword);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("size", SEARCH_RESULT_FETCH_SIZE);
            body.put("_source", List.of(
                    "entryId", "entryType", "word", "phonetic", "meaningCn", "exampleSentence",
                    "correctedEnglish", "chineseSentence", "category",
                    "bncRank", "frqRank", "wordfreqZipf", "dataQuality",
                    "visibility", "importSource", "ownerUserId", "wordbookId"
            ));

            List<Object> filters = buildScopeFilters(entryType, ownerUserId, visibility, wordbookId);

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
            String entryType,
            Long ownerUserId,
            String visibility,
            Long wordbookId
    ) {
        try {
            String normalizedWordKeyword = normalizeIndexedWord(keyword);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("size", SUGGESTION_FETCH_SIZE);
            body.put("_source", List.of("entryId", "entryType", "word", "visibility", "ownerUserId"));
            body.put("query", Map.of(
                    "bool", Map.of(
                            "filter", buildScopeFilters(entryType, ownerUserId, visibility, wordbookId),
                            "should", buildWordSearchQueries(normalizedWordKeyword),
                            "minimum_should_match", 1
                    )
            ));

            JsonNode response = sendJson("POST", "/" + INDEX_NAME + "/_search", body, false);
            return toSearchSuggestions(response.path("hits").path("hits"), keyword);
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException("Failed to query Elasticsearch suggestions", exception);
        }
    }

        private List<SearchSuggestionDto> toSearchSuggestions(JsonNode hits, String keyword) {
        List<SuggestionCandidate> candidates = new ArrayList<>();
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            MatchType matchType = determineSuggestionMatchType(TextRepairUtils.repair(source.path("word").asText()), keyword);
            if (matchType == null) {
                continue;
            }
            candidates.add(new SuggestionCandidate(
                    source.path("entryId").asLong(),
                    source.path("entryType").asText(PUBLIC_ENTRY_TYPE),
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
            if (existing == null || suggestionComparator().compare(candidate, existing) < 0) {
                deduplicated.put(key, candidate);
            }
        }

        List<SuggestionCandidate> ordered = new ArrayList<>(deduplicated.values());
        ordered.sort(suggestionComparator());
        List<SearchSuggestionDto> result = new ArrayList<>();
        for (SuggestionCandidate candidate : ordered) {
            result.add(new SearchSuggestionDto(
                    candidate.entryId(),
                    candidate.entryType(),
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
                    source.path("entryType").asText(PUBLIC_ENTRY_TYPE),
                    TextRepairUtils.repair(source.path("word").asText()),
                    SearchTextUtools.normalizePhonetic(source.path("phonetic").asText()),
                    UserFacingTextNormalizer.normalizeMeaningText(source.path("meaningCn").asText()),
                    buildSourceLabel(source.path("entryType").asText(PUBLIC_ENTRY_TYPE)),
                    resolveDisplayExampleSentence(
                            source.path("entryType").asText(PUBLIC_ENTRY_TYPE),
                            source.path("exampleSentence").asText(),
                            source.path("correctedEnglish").asText()
                    ),
                    UserFacingTextNormalizer.normalizeDisplayText(source.path("correctedEnglish").asText()),
                    UserFacingTextNormalizer.normalizeDisplayText(source.path("chineseSentence").asText()),
                    normalizeExampleAudioUrl(source.path("entryType").asText(PUBLIC_ENTRY_TYPE), source.path("exampleAudioUrl").asText()),
                    UserFacingTextNormalizer.normalizeMeaningText(source.path("category").asText()),
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
                    candidate.entryType(),
                    candidate.word(),
                    candidate.phonetic(),
                    candidate.meaningCn(),
                    candidate.source(),
                    candidate.exampleSentence(),
                    candidate.correctedEnglish(),
                    candidate.chineseSentence(),
                    candidate.exampleAudioUrl(),
                    candidate.category(),
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
                    hit.entryType(),
                    hit.word(),
                    hit.phonetic(),
                    hit.meaningCn(),
                    hit.source(),
                    hit.exampleSentence(),
                    hit.correctedEnglish(),
                    hit.chineseSentence(),
                    hit.exampleAudioUrl(),
                    hit.category(),
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
                "fields", List.of("meaningCn^3", "category^2", "exampleSentence", "correctedEnglish^2", "chineseSentence^2"),
                "type", "best_fields"
        ));
    }

    private String resolveDisplayExampleSentence(String entryType, String exampleSentence, String correctedEnglish) {
        String normalizedExampleSentence = UserFacingTextNormalizer.normalizeDisplayText(exampleSentence);
        if (hasCleanText(normalizedExampleSentence)) {
            return normalizedExampleSentence;
        }
        if (!PUBLIC_ENTRY_TYPE.equalsIgnoreCase(entryType)) {
            return normalizedExampleSentence;
        }
        return UserFacingTextNormalizer.normalizeDisplayText(correctedEnglish);
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
                || containsNormalizedText(candidate.exampleSentence(), keyword)
                || containsNormalizedText(candidate.correctedEnglish(), keyword)
                || containsNormalizedText(candidate.chineseSentence(), keyword)) {
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

    private List<Object> buildScopeFilters(String entryType, Long ownerUserId, String visibility, Long wordbookId) {
        List<Object> filters = new ArrayList<>();
        filters.add(Map.of("term", Map.of("entryType", entryType)));
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
            return new SearchScope(PUBLIC_ENTRY_TYPE, null, PUBLIC_VISIBILITY, null, true);
        }
        if (user == null || searchWordbookMapper.countOwnedWordbook(user.id(), wordbookId) == 0) {
            throw new ForbiddenException("You cannot access this wordbook");
        }
        return new SearchScope(USER_ENTRY_TYPE, user.id(), PRIVATE_VISIBILITY, wordbookId, false);
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

    private String resolveHighFrequencySourceName(String sourceName) {
        CatalogRegistry registry = loadCatalogRegistry();
        if (sourceName == null || sourceName.isBlank()) {
            return HIGH_FREQUENCY_SOURCE_NAME;
        }
        String normalized = sourceName.trim().toLowerCase(Locale.ROOT);
        if (!registry.sources().containsKey(normalized)) {
            throw new IllegalArgumentException("Unsupported public catalog source: " + sourceName);
        }
        return normalized;
    }

    private List<String> loadHighFrequencyWords(String sourceName) {
        CatalogSourceData source = loadCatalogSource(sourceName);
        if (source.words().isEmpty()) {
            throw new IllegalStateException("Public catalog source is empty: " + sourceName);
        }
        return source.words();
    }

    private CatalogSourceData loadCatalogSource(String sourceName) {
        CatalogSourceData source = loadCatalogRegistry().sources().get(sourceName);
        if (source == null) {
            throw new IllegalArgumentException("Unsupported public catalog source: " + sourceName);
        }
        return source;
    }

    private CatalogRegistry loadCatalogRegistry() {
        CatalogRegistry cached = catalogRegistryCache;
        String signature = resolveCatalogRegistrySignature();
        if (cached != null && signature.equals(cached.signature())) {
            return cached;
        }
        synchronized (this) {
            if (catalogRegistryCache != null && signature.equals(catalogRegistryCache.signature())) {
                return catalogRegistryCache;
            }
            catalogRegistryCache = readCatalogRegistry(signature);
            return catalogRegistryCache;
        }
    }

    private CatalogRegistry readCatalogRegistry(String signature) {
        LinkedHashMap<String, CatalogSourceData> sources = new LinkedHashMap<>();
        CatalogSourceData highFrequency10000 = readCatalogSourceFromClasspath(
                HIGH_FREQUENCY_SOURCE_NAME,
                ECDICT_HIGH_FREQUENCY_RESOURCE,
                MAX_HIGH_FREQUENCY_WORDS
        );
        CatalogSourceData highFrequency5000 = readCatalogSourceFromClasspath(
                HIGH_FREQUENCY_5000_SOURCE_NAME,
                ECDICT_HIGH_FREQUENCY_5000_RESOURCE,
                MAX_HIGH_FREQUENCY_WORDS
        );
        sources.put(HIGH_FREQUENCY_SOURCE_NAME, highFrequency10000);
        sources.put(HIGH_FREQUENCY_5000_SOURCE_NAME, highFrequency5000);
        sources.put(LEGACY_HIGH_FREQUENCY_5000_SOURCE_NAME, highFrequency5000);

        ExternalCatalogManifest manifest = readExternalCatalogManifest();
        if (manifest != null) {
            for (ExternalCatalogManifestSource source : manifest.sources()) {
                if (sources.containsKey(source.name())) {
                    throw new IllegalStateException("Duplicate public catalog source name: " + source.name());
                }
                Path sourceFile = manifest.baseDirectory().resolve(source.file()).normalize();
                ensureInsideExternalCatalogDirectory(manifest.baseDirectory(), sourceFile);
                CatalogSourceData data = readCatalogSourceFromPath(source.name(), sourceFile, Integer.MAX_VALUE);
                if (source.wordCount() > 0 && data.words().size() != source.wordCount()) {
                    log.warn("External public catalog source {} word count mismatch: manifest={}, actual={}",
                            source.name(),
                            source.wordCount(),
                            data.words().size());
                }
                sources.put(source.name(), data);
            }
        }

        return new CatalogRegistry(signature, Collections.unmodifiableMap(new LinkedHashMap<>(sources)));
    }

    private CatalogSourceData readCatalogSourceFromClasspath(String sourceName, String resourceName, int maxWords) {
        ClassPathResource resource = new ClassPathResource(resourceName);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return readCatalogSource(sourceName, reader, maxWords, "classpath:" + resourceName);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load public catalog source: " + sourceName, exception);
        }
    }

    private CatalogSourceData readCatalogSourceFromPath(String sourceName, Path path, int maxWords) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return readCatalogSource(sourceName, reader, maxWords, path.toString());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load external public catalog source: " + sourceName, exception);
        }
    }

    private CatalogSourceData readCatalogSource(String sourceName, BufferedReader reader, int maxWords, String descriptor) throws IOException {
        LinkedHashMap<String, EcdictCatalogEntry> entries = new LinkedHashMap<>();
        String line;
        boolean firstLine = true;
        while ((line = reader.readLine()) != null && (maxWords <= 0 || entries.size() < maxWords)) {
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
        if (entries.isEmpty()) {
            throw new IllegalStateException("Public catalog source is empty: " + descriptor);
        }
        return new CatalogSourceData(sourceName, List.copyOf(entries.keySet()), Map.copyOf(entries));
    }

    private String resolveCatalogRegistrySignature() {
        Path manifestPath = resolveExternalCatalogManifestPath();
        if (!Files.isRegularFile(manifestPath)) {
            return "classpath-only";
        }
        try {
            return manifestPath.toAbsolutePath().normalize() + ":" + Files.getLastModifiedTime(manifestPath).toMillis();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to inspect external public catalog manifest", exception);
        }
    }

    private ExternalCatalogManifest readExternalCatalogManifest() {
        Path manifestPath = resolveExternalCatalogManifestPath();
        if (!Files.isRegularFile(manifestPath)) {
            return null;
        }
        try (BufferedReader reader = Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)) {
            JsonNode root = objectMapper.readTree(reader);
            JsonNode sourceNodes = root.path("sources");
            if (!sourceNodes.isArray() || sourceNodes.isEmpty()) {
                throw new IllegalStateException("External public catalog manifest has no sources: " + manifestPath);
            }
            List<ExternalCatalogManifestSource> sources = new ArrayList<>();
            int fallbackSequence = 1;
            for (JsonNode sourceNode : sourceNodes) {
                String rawName = sourceNode.path("name").asText("");
                String rawFile = sourceNode.path("file").asText("");
                String normalizedName = rawName.trim().toLowerCase(Locale.ROOT);
                String normalizedFile = rawFile.trim();
                if (normalizedName.isBlank() || normalizedFile.isBlank()) {
                    throw new IllegalStateException("External public catalog manifest source is incomplete: " + sourceNode);
                }
                int sequence = sourceNode.path("sequence").asInt(fallbackSequence);
                int wordCount = sourceNode.path("wordCount").asInt(0);
                sources.add(new ExternalCatalogManifestSource(normalizedName, normalizedFile, wordCount, sequence));
                fallbackSequence++;
            }
            sources.sort(Comparator.comparingInt(ExternalCatalogManifestSource::sequence).thenComparing(ExternalCatalogManifestSource::name));
            return new ExternalCatalogManifest(manifestPath.getParent(), List.copyOf(sources));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read external public catalog manifest: " + manifestPath, exception);
        }
    }

    private Path resolveExternalCatalogManifestPath() {
        Path catalogRoot = resolveCatalogStorageRoot(publicCatalogSourceProperties.resolvedExternalDir());
        return catalogRoot.resolve(EXTERNAL_CATALOG_MANIFEST_FILE).normalize();
    }

    private Path resolveCatalogStorageRoot(String configuredDirectory) {
        Path configuredPath = Path.of(configuredDirectory);
        if (configuredPath.isAbsolute()) {
            return configuredPath.normalize();
        }
        return resolveProjectRoot().resolve(configuredPath).normalize();
    }

    private Path resolveProjectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path cursor = current;
        while (cursor != null) {
            if (Files.exists(cursor.resolve("docker-compose.yml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        return current;
    }

    private void ensureInsideExternalCatalogDirectory(Path root, Path target) {
        if (!target.startsWith(root)) {
            throw new IllegalStateException("Invalid external public catalog file path: " + target);
        }
    }

    private EcdictCatalogEntry findCatalogEntry(String word, String sourceName) {
        String normalizedWord = normalizeIndexedWord(word);
        if (normalizedWord.isBlank()) {
            return null;
        }
        CatalogRegistry registry = loadCatalogRegistry();
        if (sourceName != null && !sourceName.isBlank()) {
            CatalogSourceData source = registry.sources().get(sourceName.trim().toLowerCase(Locale.ROOT));
            if (source != null) {
                return source.entries().get(normalizedWord);
            }
        }
        for (CatalogSourceData source : registry.sources().values()) {
            EcdictCatalogEntry entry = source.entries().get(normalizedWord);
            if (entry != null) {
                return entry;
            }
        }
        return null;
    }

    private EcdictCatalogEntry parseEcdictCatalogLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] columns = line.split("\t", -1);
        if (columns.length < 10) {
            return null;
        }
        String word = normalizeIndexedWord(columns[0]);
        if (!supportsWordMatching(word)) {
            return null;
        }
        int metadataOffset = columns.length >= 12 ? 2 : 0;
        return new EcdictCatalogEntry(
                word,
                SearchTextUtools.normalizePhonetic(columns[1]),
                truncateText(UserFacingTextNormalizer.normalizeMeaningText(columns[2]), 255),
                truncateText(UserFacingTextNormalizer.normalizeMeaningText(columns[3]), 120),
                parseInteger(columns[4 + metadataOffset]),
                parseInteger(columns[5 + metadataOffset]),
                parseDouble(columns[6 + metadataOffset]),
                UserFacingTextNormalizer.normalizeDisplayText(columns[7 + metadataOffset]),
                UserFacingTextNormalizer.normalizeDisplayText(columns[8 + metadataOffset]),
                UserFacingTextNormalizer.normalizeDisplayText(columns[9 + metadataOffset])
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
            String normalized = sanitizeExampleCandidate(value);
            if (hasCleanText(normalized)) {
                return normalized;
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
                ImportOutcome outcome = importSingleWord(word, refreshExisting, elasticsearchAvailable, null);
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

    private DictionaryEntryPayload fetchDictionaryEntry(String word, String sourceName) {
        EcdictCatalogEntry entry = findCatalogEntry(word, sourceName);
        if (entry == null) {
            return null;
        }

        DictionaryApiExtras dictionaryApiExtras = fetchDictionaryApiExtras(entry.word());
        String example = firstNonBlank(
                entry.exampleSentence(),
                dictionaryApiExtras.exampleSentence(),
                fetchFreeDictionaryApiExample(entry.word())
        );
        String audioUrl = resolveImportedAudioUrl(entry.word(), dictionaryApiExtras.audioUrl());

        if (!isCompletePublicEntryPayload(
                entry.word(),
                entry.phonetic(),
                entry.meaningCn(),
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

    private String fetchAudioUrl(String word) {
        return resolveImportedAudioUrl(word, fetchDictionaryApiExtras(word).audioUrl());
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
            String audioUrl
    ) {
        return hasCleanText(word)
                && hasValidPhonetic(phonetic)
                && hasCleanText(meaningCn)
                && containsHanCharacter(meaningCn)
                && hasCleanText(audioUrl)
                && audioUrl.startsWith("http");
    }

    private String sanitizeExampleCandidate(String value) {
        if (!hasCleanText(value)) {
            return "";
        }
        String normalized = UserFacingTextNormalizer.normalizeDisplayText(value);
        if (isDefinitionLikeEnglishText(normalized)) {
            return "";
        }
        return normalized;
    }

    private boolean isDefinitionLikeEnglishText(String value) {
        if (!hasCleanText(value)) {
            return false;
        }
        return PART_OF_SPEECH_PREFIX_PATTERN.matcher(value.trim()).find();
    }

    private boolean isInvalidGeneratedPublicExample(String targetWord, String correctedEnglish, String chineseSentence) {
        String normalizedEnglish = UserFacingTextNormalizer.normalizeDisplayText(correctedEnglish);
        if (!hasCleanText(normalizedEnglish)
                || !hasCleanText(chineseSentence)
                || containsHanCharacter(normalizedEnglish)
                || !hasLatinLetter(normalizedEnglish)
                || isDefinitionLikeEnglishText(normalizedEnglish)) {
            return true;
        }

        String normalizedWord = TextRepairUtils.repair(targetWord).trim();
        if (hasCleanText(normalizedWord) && normalizedEnglish.equalsIgnoreCase(normalizedWord)) {
            return true;
        }
        if (!normalizedEnglish.matches(".*\\s+.*")) {
            return true;
        }
        if (countEnglishTokens(normalizedEnglish) < 4) {
            return true;
        }
        return hasCleanText(normalizedWord) && !containsTargetWord(normalizedEnglish, normalizedWord);
    }

    private int countEnglishTokens(String value) {
        int count = 0;
        java.util.regex.Matcher matcher = ENGLISH_TOKEN_PATTERN.matcher(value);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private boolean containsTargetWord(String sentence, String targetWord) {
        if (!hasCleanText(sentence) || !hasCleanText(targetWord)) {
            return false;
        }
        String normalizedSentence = sentence.toLowerCase(Locale.ROOT);
        String normalizedWord = targetWord.toLowerCase(Locale.ROOT).trim();
        if (normalizedWord.contains(" ")) {
            return normalizedSentence.contains(normalizedWord);
        }
        Pattern targetPattern = Pattern.compile("(?i)(^|[^a-z])" + Pattern.quote(normalizedWord) + "([^a-z]|$)");
        return targetPattern.matcher(sentence).find();
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
        return searchVocabularyMapper.findExistingPublicEntryId(word);
    }

    private long createPublicEntry(DictionaryEntryPayload payload) {
        PublicEntryPo row = toPublicEntryPo(payload);
        searchVocabularyMapper.insertPublicEntry(row);
        if (row.getId() != null) {
            return row.getId();
        }

        Long createdId = findPublicEntryId(payload.word());
        if (createdId != null) {
            return createdId;
        }
        throw new IllegalStateException("Failed to create public catalog entry for word: " + payload.word());
    }

    private PublicEntryPo toPublicEntryPo(DictionaryEntryPayload payload) {
        PublicEntryPo row = new PublicEntryPo();
        row.setWord(payload.word());
        row.setPhonetic(resolvePersistedPhonetic(payload.word(), payload.phonetic(), payload.importSource()));
        row.setMeaningCn(payload.meaningCn());
        row.setExampleSentence(payload.exampleSentence());
        row.setExampleAudioUrl("");
        row.setBncRank(payload.bncRank());
        row.setFrqRank(payload.frqRank());
        row.setWordfreqZipf(payload.wordfreqZipf());
        row.setExchangeInfo(payload.exchangeInfo());
        row.setDataQuality(payload.dataQuality());
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
                payload.bncRank(),
                payload.frqRank(),
                payload.wordfreqZipf(),
                payload.exchangeInfo(),
                payload.dataQuality(),
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

    private void syncEntryToIndex(String entryType, long entryId, boolean elasticsearchAvailable) {
        if (!elasticsearchAvailable) {
            return;
        }
        try {
            SearchDocumentVo row = loadSearchDocument(entryType, entryId);
            if (row != null) {
                indexDocument(row);
            }
        } catch (RuntimeException exception) {
            log.warn("Failed to index {} entry {} into Elasticsearch: {}", entryType, entryId, exception.getMessage());
        }
    }

    private void indexPublicEntry(long entryId) {
        SearchDocumentVo row = searchVocabularyMapper.findPublicDocumentById(entryId);
        if (row != null) {
            indexDocument(row);
        }
    }

    private Long findPublicEntryId(String word) {
        return searchVocabularyMapper.findPublicEntryId(word);
    }

    private SearchDocumentVo loadSearchDocument(String entryType, long entryId) {
        return PUBLIC_ENTRY_TYPE.equalsIgnoreCase(entryType)
                ? searchVocabularyMapper.findPublicDocumentById(entryId)
                : searchVocabularyMapper.findUserDocumentById(entryId);
    }

    private Map<String, PublicEnrichmentOutcome> processPublicTextTasks(
            List<PublicTaskContext> tasks,
            int batchSize,
            int workerConcurrency
    ) {
        if (tasks.isEmpty()) {
            return Map.of();
        }
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, workerConcurrency));
        try {
            List<Callable<Map<String, PublicEnrichmentOutcome>>> jobs = new ArrayList<>();
            for (List<PublicTaskContext> batch : partitionList(tasks, batchSize)) {
                jobs.add(() -> processPublicTextBatch(batch));
            }
            return mergePublicOutcomes(tasks, executor.invokeAll(jobs));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failurePublicOutcomes(tasks, "OpenAI public example generation interrupted");
        } finally {
            shutdownExecutor(executor, "public example generation");
        }
    }

    private Map<String, UserEnrichmentOutcome> processUserTextTasks(
            List<UserTaskContext> tasks,
            int batchSize,
            int workerConcurrency
    ) {
        if (tasks.isEmpty()) {
            return Map.of();
        }
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, workerConcurrency));
        try {
            List<Callable<Map<String, UserEnrichmentOutcome>>> jobs = new ArrayList<>();
            for (List<UserTaskContext> batch : partitionList(tasks, batchSize)) {
                jobs.add(() -> processUserTextBatch(batch));
            }
            return mergeUserOutcomes(tasks, executor.invokeAll(jobs));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failureUserOutcomes(tasks, "OpenAI user example enrichment interrupted");
        } finally {
            shutdownExecutor(executor, "user example enrichment");
        }
    }

    private Map<String, PublicEnrichmentOutcome> processPublicTextBatch(List<PublicTaskContext> tasks) {
        List<OpenAiExampleEnrichmentClient.PublicExampleGenerationRequest> requests = tasks.stream()
                .map(context -> new OpenAiExampleEnrichmentClient.PublicExampleGenerationRequest(
                        context.task().entryType(),
                        context.task().entryId(),
                        TextRepairUtils.repair(context.entry().getWord()),
                        UserFacingTextNormalizer.normalizeMeaningText(context.entry().getMeaningCn()),
                        UserFacingTextNormalizer.normalizeMeaningText(context.entry().getCategory()),
                        UserFacingTextNormalizer.normalizeDisplayText(context.entry().getExampleSentence())
                ))
                .toList();
        try {
            Map<String, PublicEnrichmentOutcome> outcomeByKey = new HashMap<>();
            Map<String, PublicTaskContext> contextByKey = new HashMap<>();
            for (PublicTaskContext context : tasks) {
                contextByKey.put(entryTaskKey(context.task().entryType(), context.task().entryId()), context);
            }
            for (OpenAiExampleEnrichmentClient.PublicExampleGenerationResult result : openAiExampleEnrichmentClient.generatePublicExamples(requests)) {
                String correctedEnglish = truncateText(
                        UserFacingTextNormalizer.normalizeDisplayText(result.correctedEnglish()),
                        255
                );
                String chineseSentence = truncateText(
                        UserFacingTextNormalizer.normalizeDisplayText(result.chineseSentence()),
                        255
                );
                PublicTaskContext context = contextByKey.get(entryTaskKey(result.entryType(), result.entryId()));
                String targetWord = context == null ? "" : TextRepairUtils.repair(context.entry().getWord());
                if (isInvalidGeneratedPublicExample(targetWord, correctedEnglish, chineseSentence)) {
                    outcomeByKey.put(entryTaskKey(result.entryType(), result.entryId()), PublicEnrichmentOutcome.failure("OPENAI_RESULT_INCOMPLETE"));
                    continue;
                }
                outcomeByKey.put(entryTaskKey(result.entryType(), result.entryId()), PublicEnrichmentOutcome.success(correctedEnglish, chineseSentence));
            }
            return outcomeByKey;
        } catch (Exception exception) {
            return failurePublicOutcomes(tasks, normalizeErrorMessage(exception));
        }
    }

    private Map<String, UserEnrichmentOutcome> processUserTextBatch(List<UserTaskContext> tasks) {
        List<OpenAiExampleEnrichmentClient.UserExampleEnrichmentRequest> requests = tasks.stream()
                .map(context -> new OpenAiExampleEnrichmentClient.UserExampleEnrichmentRequest(
                        context.task().entryType(),
                        context.task().entryId(),
                        context.originalEnglish()
                ))
                .toList();
        try {
            Map<String, UserEnrichmentOutcome> outcomeByKey = new HashMap<>();
            for (OpenAiExampleEnrichmentClient.UserExampleEnrichmentResult result : openAiExampleEnrichmentClient.enrichUserExamples(requests)) {
                String correctedEnglish = truncateText(
                        UserFacingTextNormalizer.normalizeDisplayText(result.correctedEnglish()),
                        255
                );
                String chineseSentence = truncateText(
                        UserFacingTextNormalizer.normalizeDisplayText(result.chineseSentence()),
                        255
                );
                if (!hasCleanText(correctedEnglish)
                        || !hasCleanText(chineseSentence)
                        || isDefinitionLikeEnglishText(correctedEnglish)) {
                    outcomeByKey.put(entryTaskKey(result.entryType(), result.entryId()), UserEnrichmentOutcome.failure("OPENAI_RESULT_INCOMPLETE"));
                    continue;
                }
                outcomeByKey.put(entryTaskKey(result.entryType(), result.entryId()), UserEnrichmentOutcome.success(correctedEnglish, chineseSentence));
            }
            return outcomeByKey;
        } catch (Exception exception) {
            return failureUserOutcomes(tasks, normalizeErrorMessage(exception));
        }
    }

    private boolean processPublicTaskCompletions(
            List<PublicTaskContext> publicTextTasks,
            List<PublicTaskContext> publicAudioOnlyTasks,
            Map<String, PublicEnrichmentOutcome> publicOutcomeByKey,
            boolean elasticsearchAvailable
    ) {
        List<Callable<Boolean>> jobs = new ArrayList<>();
        for (PublicTaskContext context : publicTextTasks) {
            jobs.add(() -> {
                String taskKey = entryTaskKey(context.task().entryType(), context.task().entryId());
                PublicEnrichmentOutcome outcome = publicOutcomeByKey.get(taskKey);
                if (outcome == null) {
                    exampleEnrichmentTaskMapper.markTaskFailed(context.task().taskId(), "OPENAI_RESULT_MISSING");
                    return false;
                }
                if (!outcome.success()) {
                    exampleEnrichmentTaskMapper.markTaskFailed(context.task().taskId(), outcome.errorMessage());
                    return false;
                }
                return finishPublicTask(context, outcome.correctedEnglish(), outcome.chineseSentence(), elasticsearchAvailable);
            });
        }
        for (PublicTaskContext context : publicAudioOnlyTasks) {
            jobs.add(() -> finishPublicTask(
                    context,
                    UserFacingTextNormalizer.normalizeDisplayText(context.entry().getCorrectedEnglish()),
                    UserFacingTextNormalizer.normalizeDisplayText(context.entry().getChineseSentence()),
                    elasticsearchAvailable
            ));
        }
        if (jobs.isEmpty()) {
            return false;
        }

        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, exampleEnrichmentProperties.resolvedTtsConcurrency()));
        try {
            boolean indexChanged = false;
            for (Future<Boolean> future : executor.invokeAll(jobs)) {
                indexChanged = resolveBooleanFuture(future) || indexChanged;
            }
            return indexChanged;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Public example completion workers were interrupted");
            return false;
        } finally {
            shutdownExecutor(executor, "public example completion");
        }
    }

    private Map<String, PublicEnrichmentOutcome> mergePublicOutcomes(
            List<PublicTaskContext> tasks,
            List<Future<Map<String, PublicEnrichmentOutcome>>> futures
    ) {
        Map<String, PublicEnrichmentOutcome> outcomeByKey = new HashMap<>();
        for (Future<Map<String, PublicEnrichmentOutcome>> future : futures) {
            try {
                outcomeByKey.putAll(future.get());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return failurePublicOutcomes(tasks, "OpenAI public example generation interrupted");
            } catch (ExecutionException exception) {
                return failurePublicOutcomes(tasks, normalizeErrorMessage(exception));
            }
        }
        return outcomeByKey;
    }

    private Map<String, UserEnrichmentOutcome> mergeUserOutcomes(
            List<UserTaskContext> tasks,
            List<Future<Map<String, UserEnrichmentOutcome>>> futures
    ) {
        Map<String, UserEnrichmentOutcome> outcomeByKey = new HashMap<>();
        for (Future<Map<String, UserEnrichmentOutcome>> future : futures) {
            try {
                outcomeByKey.putAll(future.get());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return failureUserOutcomes(tasks, "OpenAI user example enrichment interrupted");
            } catch (ExecutionException exception) {
                return failureUserOutcomes(tasks, normalizeErrorMessage(exception));
            }
        }
        return outcomeByKey;
    }

    private Map<String, PublicEnrichmentOutcome> failurePublicOutcomes(List<PublicTaskContext> tasks, String errorMessage) {
        Map<String, PublicEnrichmentOutcome> outcomeByKey = new HashMap<>();
        for (PublicTaskContext context : tasks) {
            outcomeByKey.put(entryTaskKey(context.task().entryType(), context.task().entryId()), PublicEnrichmentOutcome.failure(errorMessage));
        }
        return outcomeByKey;
    }

    private Map<String, UserEnrichmentOutcome> failureUserOutcomes(List<UserTaskContext> tasks, String errorMessage) {
        Map<String, UserEnrichmentOutcome> outcomeByKey = new HashMap<>();
        for (UserTaskContext context : tasks) {
            outcomeByKey.put(entryTaskKey(context.task().entryType(), context.task().entryId()), UserEnrichmentOutcome.failure(errorMessage));
        }
        return outcomeByKey;
    }

    private boolean resolveBooleanFuture(Future<Boolean> future) {
        try {
            return Boolean.TRUE.equals(future.get());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException exception) {
            log.warn("Public example completion worker failed: {}", normalizeErrorMessage(exception));
            return false;
        }
    }

    private <T> List<List<T>> partitionList(List<T> items, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        if (items.isEmpty()) {
            return batches;
        }
        int safeBatchSize = Math.max(1, batchSize);
        for (int index = 0; index < items.size(); index += safeBatchSize) {
            batches.add(items.subList(index, Math.min(items.size(), index + safeBatchSize)));
        }
        return batches;
    }

    private void shutdownExecutor(ExecutorService executor, String poolName) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            log.warn("{} executor shutdown was interrupted", poolName);
        }
    }

    private boolean finishPublicTask(PublicTaskContext context, String correctedEnglish, String chineseSentence, boolean elasticsearchAvailable) {
        if (isInvalidGeneratedPublicExample(TextRepairUtils.repair(context.entry().getWord()), correctedEnglish, chineseSentence)) {
            exampleEnrichmentTaskMapper.markTaskFailed(context.task().taskId(), "OPENAI_RESULT_INCOMPLETE");
            return false;
        }
        String existingCorrectedEnglish = UserFacingTextNormalizer.normalizeDisplayText(context.entry().getCorrectedEnglish());
        String existingChineseSentence = UserFacingTextNormalizer.normalizeDisplayText(context.entry().getChineseSentence());
        String exampleAudioUrl = normalizeExampleAudioUrl(PUBLIC_ENTRY_TYPE, context.entry().getExampleAudioUrl());
        if (!sameText(existingCorrectedEnglish, correctedEnglish) || !sameText(existingChineseSentence, chineseSentence)) {
            exampleAudioUrl = "";
            updateExampleEnrichment(context.task().entryType(), context.task().entryId(), correctedEnglish, chineseSentence, exampleAudioUrl);
        }
        if (!exampleEnrichmentProperties.resolvedExampleAudioEnabled()) {
            searchVocabularyMapper.updatePublicExampleAudioUrl(context.task().entryId(), exampleAudioUrl);
            exampleEnrichmentTaskMapper.markTaskSucceeded(context.task().taskId());
            syncEntryToIndex(context.task().entryType(), context.task().entryId(), elasticsearchAvailable);
            return elasticsearchAvailable;
        }

        if (!exampleAudioStorageService.hasPublicExampleAudio(context.task().entryId(), correctedEnglish)) {
            if (!openAiExampleEnrichmentClient.isSpeechConfigured()) {
                exampleEnrichmentTaskMapper.markTaskFailed(context.task().taskId(), "OPENAI_TTS_NOT_CONFIGURED");
                return false;
            }
            try {
                byte[] payload = openAiExampleEnrichmentClient.synthesizeSpeech(correctedEnglish);
                exampleAudioStorageService.storePublicExampleAudio(context.task().entryId(), correctedEnglish, payload);
                exampleAudioUrl = exampleAudioStorageService.publicExampleAudioUrl(context.task().entryId());
            } catch (Exception exception) {
                exampleEnrichmentTaskMapper.markTaskFailed(context.task().taskId(), normalizeErrorMessage(exception));
                return false;
            }
        } else if (!hasCleanText(exampleAudioUrl)) {
            exampleAudioUrl = exampleAudioStorageService.publicExampleAudioUrl(context.task().entryId());
        }

        searchVocabularyMapper.updatePublicExampleAudioUrl(context.task().entryId(), exampleAudioUrl);
        exampleEnrichmentTaskMapper.markTaskSucceeded(context.task().taskId());
        syncEntryToIndex(context.task().entryType(), context.task().entryId(), elasticsearchAvailable);
        return elasticsearchAvailable;
    }

    private boolean needsPublicExampleText(SearchDocumentVo entry) {
        return !hasCleanText(entry.getCorrectedEnglish())
                || !hasCleanText(entry.getChineseSentence())
                || isInvalidGeneratedPublicExample(
                TextRepairUtils.repair(entry.getWord()),
                UserFacingTextNormalizer.normalizeDisplayText(entry.getCorrectedEnglish()),
                UserFacingTextNormalizer.normalizeDisplayText(entry.getChineseSentence())
        );
    }

    private boolean needsPublicExampleAudio(SearchDocumentVo entry) {
        if (!exampleEnrichmentProperties.resolvedExampleAudioEnabled()) {
            return false;
        }
        String correctedEnglish = UserFacingTextNormalizer.normalizeDisplayText(entry.getCorrectedEnglish());
        if (!hasCleanText(correctedEnglish)) {
            return false;
        }
        return !hasCleanText(entry.getExampleAudioUrl())
                || !exampleAudioStorageService.hasPublicExampleAudio(entry.getEntryId(), correctedEnglish);
    }

    private String normalizeExampleAudioUrl(String entryType, String exampleAudioUrl) {
        if (!PUBLIC_ENTRY_TYPE.equalsIgnoreCase(entryType)) {
            return "";
        }
        String normalized = exampleAudioUrl == null ? "" : exampleAudioUrl.trim();
        return normalized.isBlank() ? "" : normalized;
    }

    private void updateExampleEnrichment(String entryType, long entryId, String correctedEnglish, String chineseSentence, String exampleAudioUrl) {
        if (PUBLIC_ENTRY_TYPE.equalsIgnoreCase(entryType)) {
            searchVocabularyMapper.updatePublicExampleEnrichment(entryId, correctedEnglish, chineseSentence, exampleAudioUrl);
            return;
        }
        searchVocabularyMapper.updateUserExampleEnrichment(entryId, correctedEnglish, chineseSentence);
    }

    private String entryTaskKey(String entryType, long entryId) {
        return entryType + ":" + entryId;
    }

    private DetailVo loadDetailRow(long entryId, VocabularyEntryType entryType) {
        DetailVo row = entryType == VocabularyEntryType.PUBLIC
                ? searchVocabularyMapper.loadPublicDetailRow(entryId)
                : searchVocabularyMapper.loadUserDetailRow(entryId);
        if (row == null) {
            throw new NotFoundException("Word not found");
        }
        return row;
    }

    private List<SearchDocumentVo> loadAllRows() {
        List<SearchDocumentVo> rows = new ArrayList<>();
        rows.addAll(searchVocabularyMapper.loadAllPublicRows());
        rows.addAll(searchVocabularyMapper.loadAllUserRows());
        return rows;
    }

    private ImportOutcome importSingleWord(String word, boolean refreshExisting, boolean elasticsearchAvailable, String sourceName) {
        Long existingId = findExistingPublicEntryId(word);
        if (existingId != null && !refreshExisting) {
            return new ImportOutcome(ImportAction.SKIPPED, existingId, null, false);
        }

        DictionaryEntryPayload payload = fetchDictionaryEntry(word, sourceName);
        if (payload == null) {
            return new ImportOutcome(ImportAction.FAILED, existingId, "INCOMPLETE_OR_MISSING_DICTIONARY_PAYLOAD", false);
        }

        if (existingId == null) {
            long entryId = createPublicEntry(payload);
            exampleEnrichmentTaskMapper.upsertTask(PUBLIC_ENTRY_TYPE, entryId);
            syncPublicEntryToIndex(entryId, elasticsearchAvailable);
            return new ImportOutcome(ImportAction.IMPORTED, entryId, null, hasCleanText(payload.exampleSentence()));
        }

        updatePublicEntry(existingId, payload);
        exampleEnrichmentTaskMapper.upsertTask(PUBLIC_ENTRY_TYPE, existingId);
        syncPublicEntryToIndex(existingId, elasticsearchAvailable);
        return new ImportOutcome(ImportAction.UPDATED, existingId, null, hasCleanText(payload.exampleSentence()));
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

    private boolean shouldEnrichExampleSentence(String value) {
        if (!hasCleanText(value)) {
            return false;
        }
        String normalized = UserFacingTextNormalizer.normalizeDisplayText(value);
        if (normalized.isBlank()
                || containsHanCharacter(normalized)
                || !hasLatinLetter(normalized)
                || isDefinitionLikeEnglishText(normalized)) {
            return false;
        }

        int wordCount = 0;
        for (String token : normalized.split("\\s+")) {
            if (token.chars().anyMatch(Character::isLetter)) {
                wordCount++;
            }
        }
        return wordCount >= 2 || normalized.matches(".*[.!?,;:].*");
    }

    private int normalizeDatabaseContent() {
        int updated = 0;
        updated += normalizeVocabularyEntries();
        updated += normalizeWordbooks();
        return updated;
    }

    private int normalizeVocabularyEntries() {
        int updated = 0;
        for (VocabularyCleanupVo row : searchVocabularyMapper.loadPublicVocabularyCleanupRows()) {
            String phonetic = resolvePersistedPhonetic(row.getWord(), row.getPhonetic(), row.getImportSource());
            String meaning = UserFacingTextNormalizer.normalizeMeaningText(row.getMeaningCn());
            String example = UserFacingTextNormalizer.normalizeDisplayText(row.getExampleSentence());
            String audioUrl = resolvePersistedAudioUrl(row.getWord(), row.getAudioUrl(), row.getImportSource());
            if (!sameText(row.getPhonetic(), phonetic)
                    || !sameText(row.getMeaningCn(), meaning)
                    || !sameText(row.getExampleSentence(), example)
                    || !sameText(row.getAudioUrl(), audioUrl)) {
                searchVocabularyMapper.updatePublicVocabularyCleanup(row.getId(), phonetic, meaning, example, audioUrl);
                updated++;
            }
        }
        for (VocabularyCleanupVo row : searchVocabularyMapper.loadUserVocabularyCleanupRows()) {
            String phonetic = resolvePersistedPhonetic(row.getWord(), row.getPhonetic(), row.getImportSource());
            String meaning = UserFacingTextNormalizer.normalizeMeaningText(row.getMeaningCn());
            String example = UserFacingTextNormalizer.normalizeDisplayText(row.getExampleSentence());
            String category = UserFacingTextNormalizer.normalizeMeaningText(row.getCategory());
            if (!sameText(row.getPhonetic(), phonetic)
                    || !sameText(row.getMeaningCn(), meaning)
                    || !sameText(row.getExampleSentence(), example)
                    || !sameText(row.getCategory(), category)) {
                searchVocabularyMapper.updateUserVocabularyCleanup(row.getId(), phonetic, meaning, example, category);
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
        EcdictCatalogEntry catalogEntry = findCatalogEntry(word, null);
        if (catalogEntry == null || PhoneticNormalizer.hasPlaceholder(catalogEntry.phonetic())) {
            return normalized;
        }
        return catalogEntry.phonetic();
    }

    private String resolvePersistedAudioUrl(String word, String audioUrl, String importSource) {
        String normalized = SearchTextUtools.normalizeAudioUrl(audioUrl);
        if (hasCleanText(normalized)) {
            return normalized;
        }
        if (!PUBLIC_IMPORT_SOURCE.equalsIgnoreCase(SearchTextUtools.normalizeImportSource(importSource))) {
            return normalized;
        }
        if (word == null || word.isBlank() || !shouldHydrate(word)) {
            return normalized;
        }
        return buildFallbackAudioUrl(word);
    }

    private String resolveImportedAudioUrl(String word, String audioUrl) {
        String normalized = SearchTextUtools.normalizeAudioUrl(audioUrl);
        if (hasCleanText(normalized)) {
            return normalized;
        }
        return buildFallbackAudioUrl(word);
    }

    private String toClientAudioUrl(String audioUrl) {
        String normalized = SearchTextUtools.normalizeAudioUrl(audioUrl);
        if (!hasCleanText(normalized)) {
            return "";
        }
        return "/search/audio-proxy?src=" + URLEncoder.encode(normalized, StandardCharsets.UTF_8);
    }

    private String buildFallbackAudioUrl(String word) {
        if (word == null || word.isBlank() || !shouldHydrate(word)) {
            return "";
        }
        return AUDIO_FALLBACK_BASE_URL + URLEncoder.encode(word.trim(), StandardCharsets.UTF_8);
    }

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
    private boolean sameText(String raw, String normalized) {
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

    private String buildSourceLabel(String entryType) {
        return PUBLIC_ENTRY_TYPE.equalsIgnoreCase(entryType) ? PUBLIC_SOURCE_LABEL : PRIVATE_SOURCE_LABEL;
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
                                Map.entry("entryType", Map.of("type", "keyword")),
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
                                Map.entry("correctedEnglish", Map.of("type", "text")),
                                Map.entry("chineseSentence", Map.of("type", "text")),
                                Map.entry("exampleAudioUrl", Map.of("type", "keyword")),
                                Map.entry("category", Map.of("type", "text")),
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

        private void indexDocument(SearchDocumentVo row) {
        try {
            String normalizedWord = TextRepairUtils.repair(row.getWord());
            String normalizedPhonetic = SearchTextUtools.normalizePhonetic(row.getPhonetic());
            String normalizedMeaning = UserFacingTextNormalizer.normalizeMeaningText(row.getMeaningCn());
            String normalizedExample = UserFacingTextNormalizer.normalizeDisplayText(row.getExampleSentence());
            String normalizedCorrectedEnglish = UserFacingTextNormalizer.normalizeDisplayText(row.getCorrectedEnglish());
            String normalizedChineseSentence = UserFacingTextNormalizer.normalizeDisplayText(row.getChineseSentence());
            String normalizedExampleAudioUrl = normalizeExampleAudioUrl(row.getEntryType(), row.getExampleAudioUrl());
            String normalizedCategory = UserFacingTextNormalizer.normalizeMeaningText(row.getCategory());
            String normalizedDataQuality = UserFacingTextNormalizer.normalizeDisplayText(row.getDataQuality());
            String normalizedImportSource = SearchTextUtools.normalizeImportSource(row.getImportSource());

            Map<String, Object> document = new LinkedHashMap<>();
            document.put("entryId", row.getEntryId());
            document.put("entryType", row.getEntryType());
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
            document.put("correctedEnglish", normalizedCorrectedEnglish);
            document.put("chineseSentence", normalizedChineseSentence);
            document.put("exampleAudioUrl", normalizedExampleAudioUrl);
            document.put("category", normalizedCategory);
            document.put("bncRank", row.getBncRank());
            document.put("frqRank", row.getFrqRank());
            document.put("wordfreqZipf", row.getWordfreqZipf());
            document.put("dataQuality", normalizedDataQuality);
            document.put("importSource", normalizedImportSource);
            sendJson("PUT", "/" + INDEX_NAME + "/_doc/" + row.getEntryType() + "-" + row.getEntryId(), document, false);
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
            String entryType,
            String word,
            String phonetic,
            String meaningCn,
            String source,
            String exampleSentence,
            String correctedEnglish,
            String chineseSentence,
            String exampleAudioUrl,
            String category,
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
            String entryType,
            String word,
            String phonetic,
            String meaningCn,
            String source,
            String exampleSentence,
            String correctedEnglish,
            String chineseSentence,
            String exampleAudioUrl,
            String category,
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

    private record ClaimedEnrichmentTask(
            long taskId,
            String entryType,
            long entryId
    ) {
    }

    private record PublicTaskContext(
            ClaimedEnrichmentTask task,
            SearchDocumentVo entry
    ) {
    }

    private record UserTaskContext(
            ClaimedEnrichmentTask task,
            SearchDocumentVo entry,
            String originalEnglish
    ) {
    }

    private record PublicEnrichmentOutcome(
            String correctedEnglish,
            String chineseSentence,
            String errorMessage
    ) {
        static PublicEnrichmentOutcome success(String correctedEnglish, String chineseSentence) {
            return new PublicEnrichmentOutcome(correctedEnglish, chineseSentence, "");
        }

        static PublicEnrichmentOutcome failure(String errorMessage) {
            return new PublicEnrichmentOutcome("", "", errorMessage);
        }

        boolean success() {
            return errorMessage == null || errorMessage.isBlank();
        }
    }

    private record UserEnrichmentOutcome(
            String correctedEnglish,
            String chineseSentence,
            String errorMessage
    ) {
        static UserEnrichmentOutcome success(String correctedEnglish, String chineseSentence) {
            return new UserEnrichmentOutcome(correctedEnglish, chineseSentence, "");
        }

        static UserEnrichmentOutcome failure(String errorMessage) {
            return new UserEnrichmentOutcome("", "", errorMessage);
        }

        boolean success() {
            return errorMessage == null || errorMessage.isBlank();
        }
    }

    private record CatalogRegistry(
            String signature,
            Map<String, CatalogSourceData> sources
    ) {
    }

    private record CatalogSourceData(
            String sourceName,
            List<String> words,
            Map<String, EcdictCatalogEntry> entries
    ) {
    }

    private record ExternalCatalogManifest(
            Path baseDirectory,
            List<ExternalCatalogManifestSource> sources
    ) {
    }

    private record ExternalCatalogManifestSource(
            String name,
            String file,
            int wordCount,
            int sequence
    ) {
    }

    private record DictionaryEntryPayload(
            String word,
            String phonetic,
            String meaningCn,
            String exampleSentence,
            String category,
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
            String entryType,
            String word,
            String visibility,
            Long ownerUserId,
            double score,
            MatchType matchType
    ) {
    }

    private record SearchScope(
            String entryType,
            Long ownerUserId,
            String visibility,
            Long wordbookId,
            boolean allowHydrate
    ) {
    }

    private record ImportOutcome(
            ImportAction action,
            Long entryId,
            String errorMessage,
            boolean hasExample
    ) {
    }

    private record ImportTaskResult(
            long itemId,
            boolean claimed,
            ImportOutcome outcome
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

