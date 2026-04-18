package com.nightfall.englishnova.search.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nightfall.englishnova.search.domain.po.PublicEntryPo;
import com.nightfall.englishnova.search.domain.po.PublicWordbookPo;
import com.nightfall.englishnova.search.domain.vo.DetailVo;
import com.nightfall.englishnova.search.domain.vo.ImportTaskCleanupVo;
import com.nightfall.englishnova.search.domain.vo.SearchDocumentVo;
import com.nightfall.englishnova.search.domain.vo.StudyFocusCleanupVo;
import com.nightfall.englishnova.search.domain.vo.VocabularyCleanupVo;
import com.nightfall.englishnova.search.domain.vo.WordbookCleanupVo;
import com.nightfall.englishnova.search.mapper.SearchImportTaskMapper;
import com.nightfall.englishnova.search.mapper.SearchStudyFocusMapper;
import com.nightfall.englishnova.search.mapper.SearchVocabularyMapper;
import com.nightfall.englishnova.search.mapper.SearchWordbookMapper;
import com.nightfall.englishnova.search.service.SearchCatalogService;
import com.nightfall.englishnova.search.utools.SearchTextUtools;
import com.nightfall.englishnova.shared.auth.CurrentUser;
import com.nightfall.englishnova.shared.dto.PublicCatalogImportRequest;
import com.nightfall.englishnova.shared.dto.PublicCatalogImportResultDto;
import com.nightfall.englishnova.shared.dto.SearchHitDto;
import com.nightfall.englishnova.shared.dto.SearchSuggestionDto;
import com.nightfall.englishnova.shared.dto.WordDetailDto;
import com.nightfall.englishnova.shared.dto.WordSearchResponseDto;
import com.nightfall.englishnova.shared.events.WordbookImportedEvent;
import com.nightfall.englishnova.shared.exception.ForbiddenException;
import com.nightfall.englishnova.shared.exception.NotFoundException;
import com.nightfall.englishnova.shared.text.TextRepairUtils;
import com.nightfall.englishnova.shared.text.UserFacingTextNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    private static final String PUBLIC_SOURCE_LABEL = "Public Catalog - FreeDictionaryAPI.com";
    private static final String PRIVATE_SOURCE_LABEL = "My Wordbook";
    private static final String PUBLIC_WORDBOOK_NAME = "English Nova Public Catalog";
    private static final String PUBLIC_WORDBOOK_SOURCE = "FreeDictionaryAPI.com / Wiktionary";
    private static final String PUBLIC_WORDBOOK_PLATFORM = "DICTIONARY_API";
    private static final String PUBLIC_IMPORT_SOURCE = "free-dictionary-api";
    private static final int MAX_IMPORT_WORDS = 500;
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int SEARCH_RESULT_SIZE = 18;
    private static final int SEARCH_RESULT_FETCH_SIZE = 60;
    private static final int SUGGESTION_FETCH_SIZE = 40;
    private static final int SUGGESTION_LIMIT = 10;
    private static final String TRANSLATION_API_BASE_URL = "https://freedictionaryapi.com/api/v1";
    private static final String AUDIO_API_BASE_URL = "https://api.dictionaryapi.dev/api/v2/entries/en";

    private final SearchVocabularyMapper searchVocabularyMapper;
    private final SearchWordbookMapper searchWordbookMapper;
    private final SearchImportTaskMapper searchImportTaskMapper;
    private final SearchStudyFocusMapper searchStudyFocusMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String elasticsearchBaseUrl;

    public SearchCatalogServiceImpl(
            SearchVocabularyMapper searchVocabularyMapper,
            SearchWordbookMapper searchWordbookMapper,
            SearchImportTaskMapper searchImportTaskMapper,
            SearchStudyFocusMapper searchStudyFocusMapper,
            ObjectMapper objectMapper,
            @Value("${spring.elasticsearch.uris}") String elasticsearchBaseUrl
    ) {
        this.searchVocabularyMapper = searchVocabularyMapper;
        this.searchWordbookMapper = searchWordbookMapper;
        this.searchImportTaskMapper = searchImportTaskMapper;
        this.searchStudyFocusMapper = searchStudyFocusMapper;
        this.objectMapper = objectMapper;
        this.elasticsearchBaseUrl = elasticsearchBaseUrl.endsWith("/")
                ? elasticsearchBaseUrl.substring(0, elasticsearchBaseUrl.length() - 1)
                : elasticsearchBaseUrl;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * 同时搜索公共词库和当前用户的私有词库。
     */
    public WordSearchResponseDto searchVocabulary(String keyword, CurrentUser user) {
        String normalizedKeyword = SearchTextUtools.normalizeSearchKeyword(keyword);
        if (normalizedKeyword.isBlank()) {
            return new WordSearchResponseDto(List.of(), List.of());
        }

        ensureIndex();
        List<SearchHitDto> publicHits = searchByScope(normalizedKeyword, null, PUBLIC_VISIBILITY);
        if (publicHits.isEmpty() && shouldHydrate(normalizedKeyword)) {
            importWords(List.of(normalizedKeyword), false);
            publicHits = searchByScope(normalizedKeyword, null, PUBLIC_VISIBILITY);
        }

        List<SearchHitDto> myHits = user == null
                ? List.of()
                : searchByScope(normalizedKeyword, user.id(), PRIVATE_VISIBILITY);
        return new WordSearchResponseDto(publicHits, myHits);
    }

    /**
     * 当关键字看起来像单词查询时，返回搜索建议。
     */
    public List<SearchSuggestionDto> searchSuggestions(String keyword, CurrentUser user) {
        String normalizedKeyword = SearchTextUtools.normalizeSearchKeyword(keyword);
        if (normalizedKeyword.isBlank() || !shouldHydrate(normalizedKeyword)) {
            return List.of();
        }

        ensureIndex();
        return searchSuggestionsByWordMatch(normalizedKeyword, user);
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
    private List<SearchHitDto> searchByScope(String keyword, Long ownerUserId, String visibility) {
        try {
            String normalizedWordKeyword = normalizeIndexedWord(keyword);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("size", SEARCH_RESULT_FETCH_SIZE);
            body.put("_source", List.of(
                    "entryId", "word", "phonetic", "meaningCn", "exampleSentence", "category", "visibility", "importSource"
            ));

            List<Object> filters = new ArrayList<>();
            filters.add(Map.of("term", Map.of("visibility", visibility)));
            if (ownerUserId != null) {
                filters.add(Map.of("term", Map.of("ownerUserId", ownerUserId)));
            }

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

    private List<SearchSuggestionDto> searchSuggestionsByWordMatch(String keyword, CurrentUser user) {
        try {
            String normalizedWordKeyword = normalizeIndexedWord(keyword);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("size", SUGGESTION_FETCH_SIZE);
            body.put("_source", List.of("entryId", "word", "visibility", "ownerUserId"));
            body.put("query", Map.of(
                    "bool", Map.of(
                            "filter", List.of(buildSuggestionVisibilityFilter(user)),
                            "should", buildWordSearchQueries(normalizedWordKeyword),
                            "minimum_should_match", 1
                    )
            ));

            JsonNode response = sendJson("POST", "/" + INDEX_NAME + "/_search", body, false);
            return toSearchSuggestions(response.path("hits").path("hits"), user, keyword);
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
                "fields", List.of("meaningCn^3", "category^2", "exampleSentence"),
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

    private Comparator<RankedSearchHit> searchHitComparator() {
        return Comparator
                .comparingInt((RankedSearchHit hit) -> hit.matchType().rank())
                .thenComparingInt(hit -> hit.word().length())
                .thenComparing(Comparator.comparingDouble(RankedSearchHit::score).reversed())
                .thenComparing(hit -> normalizeIndexedWord(hit.word()))
                .thenComparingLong(RankedSearchHit::entryId);
    }

    private Comparator<SuggestionCandidate> suggestionComparator() {
        return Comparator
                .comparingInt((SuggestionCandidate candidate) -> candidate.matchType().rank())
                .thenComparingInt(candidate -> candidate.word().length())
                .thenComparing(Comparator.comparingDouble(SuggestionCandidate::score).reversed())
                .thenComparing(candidate -> normalizeIndexedWord(candidate.word()))
                .thenComparingLong(SuggestionCandidate::entryId);
    }

    private PublicCatalogImportResultDto importWords(List<String> words, boolean refreshExisting) {
        boolean elasticsearchAvailable = tryEnsureIndex();
        List<String> imported = new ArrayList<>();
        List<String> updated = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        boolean indexChanged = false;

        for (String word : words) {
            try {
                ImportAction action = importSingleWord(word, refreshExisting, elasticsearchAvailable);
                switch (action) {
                    case IMPORTED -> {
                        imported.add(word);
                        indexChanged = true;
                    }
                    case UPDATED -> {
                        updated.add(word);
                        indexChanged = true;
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

    // 获取一个词典结果，并规范化成数据库落库需要的字段。
    private DictionaryEntryPayload fetchDictionaryEntry(String word) {
        try {
            String encodedWord = URLEncoder.encode(word, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TRANSLATION_API_BASE_URL + "/entries/en/" + encodedWord + "?translations=true"))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400 || response.body() == null || response.body().isBlank()) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode entries = root.path("entries");
            if (!entries.isArray() || entries.isEmpty()) {
                return null;
            }

            Set<String> translations = new LinkedHashSet<>();
            Set<String> partsOfSpeech = new LinkedHashSet<>();
            String phonetic = "";
            String example = "";

            for (JsonNode entry : entries) {
                if (!"en".equalsIgnoreCase(entry.path("language").path("code").asText("en"))) {
                    continue;
                }

                String partOfSpeech = UserFacingTextNormalizer.normalizeMeaningText(entry.path("partOfSpeech").asText());
                if (!partOfSpeech.isBlank()) {
                    partsOfSpeech.add(partOfSpeech);
                }

                if (phonetic.isBlank()) {
                    phonetic = extractPhonetic(entry.path("pronunciations"));
                }
                if (example.isBlank()) {
                    example = extractExample(entry.path("senses"));
                }
                collectChineseTranslations(entry.path("senses"), translations);
            }

            if (translations.isEmpty()) {
                return null;
            }

            return new DictionaryEntryPayload(
                    TextRepairUtils.repair(word),
                    SearchTextUtools.normalizePhonetic(phonetic.isBlank() ? "-" : phonetic),
                    UserFacingTextNormalizer.normalizeMeaningText(String.join(" / ", translations.stream().limit(4).toList())),
                    example.isBlank() ? "Imported from FreeDictionaryAPI.com" : UserFacingTextNormalizer.normalizeDisplayText(example),
                    partsOfSpeech.isEmpty() ? "dictionary" : UserFacingTextNormalizer.normalizeMeaningText(String.join(" / ", partsOfSpeech)),
                    SearchTextUtools.scoreDifficulty(word),
                    "",
                    PUBLIC_IMPORT_SOURCE
            );
        } catch (IOException | InterruptedException exception) {
            return null;
        }
    }

    // 当当前词条没有音频时，从 dictionaryapi.dev 拉取音频地址。
    private String fetchAudioUrl(String word) {
        try {
            String encodedWord = URLEncoder.encode(word, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(AUDIO_API_BASE_URL + "/" + encodedWord))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400 || response.body() == null || response.body().isBlank()) {
                return "";
            }

            JsonNode entries = objectMapper.readTree(response.body());
            if (!entries.isArray()) {
                return "";
            }

            for (JsonNode entry : entries) {
                JsonNode phonetics = entry.path("phonetics");
                if (!phonetics.isArray()) {
                    continue;
                }
                for (JsonNode phonetic : phonetics) {
                    String audio = phonetic.path("audio").asText();
                    if (audio != null && !audio.isBlank()) {
                        return audio;
                    }
                }
            }
            return "";
        } catch (IOException | InterruptedException exception) {
            return "";
        }
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
                UserFacingTextNormalizer.normalizeDisplayText(PUBLIC_WORDBOOK_SOURCE)
        );
    }

    // 按需创建共享公共词书。
    private long ensurePublicWordbook() {
        Long existingId = findPublicWordbookId();
        if (existingId != null) {
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
        row.setPhonetic(payload.phonetic());
        row.setMeaningCn(payload.meaningCn());
        row.setExampleSentence(payload.exampleSentence());
        row.setCategory(payload.category());
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
                payload.phonetic(),
                payload.meaningCn(),
                payload.exampleSentence(),
                payload.category(),
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
    private ImportAction importSingleWord(String word, boolean refreshExisting, boolean elasticsearchAvailable) {
        Long existingId = findExistingPublicEntryId(word);
        if (existingId != null && !refreshExisting) {
            return ImportAction.SKIPPED;
        }

        DictionaryEntryPayload payload = fetchDictionaryEntry(word);
        if (payload == null) {
            return ImportAction.FAILED;
        }

        long wordbookId = ensurePublicWordbook();
        if (existingId == null) {
            long entryId = createPublicEntry(wordbookId, payload);
            syncPublicWordbookCount(wordbookId);
            syncPublicEntryToIndex(entryId, elasticsearchAvailable);
            return ImportAction.IMPORTED;
        }

        updatePublicEntry(existingId, payload);
        syncPublicEntryToIndex(existingId, elasticsearchAvailable);
        return ImportAction.UPDATED;
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
            String meaning = UserFacingTextNormalizer.normalizeMeaningText(row.getMeaningCn());
            String example = UserFacingTextNormalizer.normalizeDisplayText(row.getExampleSentence());
            String category = UserFacingTextNormalizer.normalizeMeaningText(row.getCategory());
            if (!sameText(row.getMeaningCn(), meaning) || !sameText(row.getExampleSentence(), example) || !sameText(row.getCategory(), category)) {
                searchVocabularyMapper.updateVocabularyCleanup(row.getId(), meaning, example, category);
                updated++;
            }
        }
        return updated;
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
            int difficulty,
            String audioUrl,
            String importSource
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

