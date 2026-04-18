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
 * 鎼滅储鐩綍鏈嶅姟銆傛彁渚?Elasticsearch 鍏ㄦ枃妫€绱€佹悳绱㈠缓璁€佽瘝鏉¤鎯呫€佸叕鍏辫瘝搴撳鍏ュ強绱㈠紩鍚屾绛夋牳蹇冭兘鍔涖€?
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

    /**
     * 鏋勯€犳悳绱㈢洰褰曟湇鍔°€?
     *
     * @param searchVocabularyMapper vocabulary persistence mapper
     * @param searchWordbookMapper    wordbook persistence mapper
     * @param searchImportTaskMapper  import task persistence mapper
     * @param searchStudyFocusMapper  study focus persistence mapper
     * @param objectMapper          JSON 搴忓垪鍖栧伐鍏?
     * @param elasticsearchBaseUrl  Elasticsearch 鍩虹 URL
     */
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
     * 鏍规嵁鍏抽敭璇嶆悳绱㈠崟璇嶏紝鍚屾椂杩斿洖鍏叡璇嶅簱涓庣敤鎴风鏈夎瘝搴撶殑鍖归厤缁撴灉銆?
     *
     * @param keyword 鎼滅储鍏抽敭璇?
     * @param user   褰撳墠鐢ㄦ埛锛屽彲涓?null锛堟湭鐧诲綍鏃跺彧杩斿洖鍏叡缁撴灉锛?
     * @return 鎼滅储缁撴灉锛屽寘鍚叕鍏卞懡涓拰绉佹湁鍛戒腑
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
     * 鏍规嵁鍏抽敭璇嶈幏鍙栨悳绱㈠缓璁垪琛ㄣ€?
     *
     * @param keyword 鎼滅储鍏抽敭璇?
     * @param user   褰撳墠鐢ㄦ埛锛屽彲涓?null
     * @return 鎼滅储寤鸿鍒楄〃
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
     * 鑾峰彇鎸囧畾璇嶆潯鐨勮鎯呬俊鎭€?
     *
     * @param entryId 璇嶆潯 ID
     * @param user    褰撳墠鐢ㄦ埛锛岀敤浜庢潈闄愭牎楠?
     * @return 鍗曡瘝璇︽儏
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
     * 瀵煎叆鍏叡璇嶅簱璇嶆潯銆?
     *
     * @param request 瀵煎叆璇锋眰锛屽彲涓?null锛堜娇鐢ㄩ粯璁ょ瀛愯瘝锛?
     * @return 瀵煎叆缁撴灉
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
     * 搴旂敤鍚姩鍚庨噸寤?Elasticsearch 绱㈠紩锛氭爣鍑嗗寲鏁版嵁搴撳唴瀹瑰苟閲嶅缓鍏ㄦ枃绱㈠紩銆?
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
     * 澶勭悊璇嶄功瀵煎叆浜嬩欢锛屽皢鏂板鍏ョ殑璇嶆潯鍚屾鍒?Elasticsearch 绱㈠紩銆?
     *
     * @param event 璇嶄功瀵煎叆浜嬩欢
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

    /** 鎸夊彲瑙佹€ц寖鍥村湪 Elasticsearch 涓悳绱㈣瘝鏉°€?*/
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

    /** 鎸夎瘝鍓嶇紑/閫氶厤/寤鸿绛夋ā寮忓湪 Elasticsearch 涓悳绱㈠缓璁€?*/
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

    /** 鏋勫缓寤鸿鏌ヨ鐨勫彲瑙佹€ц繃婊ゆ潯浠躲€?*/
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

    /** 灏?Elasticsearch 鍛戒腑缁撴灉杞崲涓烘悳绱㈠懡涓?DTO 鍒楄〃銆?*/
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

    /** 灏?Elasticsearch 鍛戒腑缁撴灉杞崲涓烘悳绱㈠缓璁垪琛ㄣ€?*/
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

    /** 鍒ゆ柇鏄惁搴斾娇鐢ㄦ柊寤鸿鏇挎崲宸叉湁寤鸿銆?*/
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

    /** 鏋勫缓璇箟鏂囨湰鎼滅储鏌ヨ锛堥拡瀵?meaningCn / category / exampleSentence锛夈€?*/
    private Map<String, Object> buildTextSearchQuery(String keyword) {
        return Map.of("multi_match", Map.of(
                "query", keyword,
                "fields", List.of("meaningCn^3", "category^2", "exampleSentence"),
                "type", "best_fields"
        ));
    }

    /** 鏋勫缓鍗曡瘝绮剧‘/鍓嶇紑/閫氶厤/寤鸿鐨勫鍚堟悳绱㈡煡璇€?*/
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

    /** 鍒ゆ柇鎼滅储鍛戒腑缁撴灉鐨勫尮閰嶇被鍨嬨€?*/
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

    /** 鍒ゆ柇寤鸿鍖归厤绫诲瀷锛堢簿纭?鍓嶇紑/鍖呭惈锛夈€?*/
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

    /** 鍒ゆ柇鏂囨湰涓槸鍚﹀寘鍚爣鍑嗗寲鐨勫叧閿瘝銆?*/
    private boolean containsNormalizedText(String value, String keyword) {
        if (value == null || value.isBlank() || keyword == null || keyword.isBlank()) {
            return false;
        }
        return UserFacingTextNormalizer.normalizeDisplayText(value)
                .toLowerCase(Locale.ROOT)
                .contains(keyword.toLowerCase(Locale.ROOT));
    }

    /** 鍒ゆ柇鍏抽敭璇嶆槸鍚︽敮鎸佸崟璇嶅尮閰嶆ā寮忋€?*/
    private boolean supportsWordMatching(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return false;
        }
        return normalizeIndexedWord(keyword).matches("[a-z][a-z\\-']*");
    }

    /** 鏍囧噯鍖栧凡绱㈠紩鍗曡瘝锛堜慨澶嶄贡鐮?+ 灏忓啓锛夈€?*/
    private String normalizeIndexedWord(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return TextRepairUtils.repair(value).trim().toLowerCase(Locale.ROOT);
    }

    /** 鎼滅储鍛戒腑鐨勬帓搴忔瘮杈冨櫒銆?*/
    private Comparator<RankedSearchHit> searchHitComparator() {
        return Comparator
                .comparingInt((RankedSearchHit hit) -> hit.matchType().rank())
                .thenComparingInt(hit -> hit.word().length())
                .thenComparing(Comparator.comparingDouble(RankedSearchHit::score).reversed())
                .thenComparing(hit -> normalizeIndexedWord(hit.word()))
                .thenComparingLong(RankedSearchHit::entryId);
    }

    /** 鎼滅储寤鸿鐨勬帓搴忔瘮杈冨櫒銆?*/
    private Comparator<SuggestionCandidate> suggestionComparator() {
        return Comparator
                .comparingInt((SuggestionCandidate candidate) -> candidate.matchType().rank())
                .thenComparingInt(candidate -> candidate.word().length())
                .thenComparing(Comparator.comparingDouble(SuggestionCandidate::score).reversed())
                .thenComparing(candidate -> normalizeIndexedWord(candidate.word()))
                .thenComparingLong(SuggestionCandidate::entryId);
    }

    /** 鎵归噺瀵煎叆鍗曡瘝鍒楄〃鍒板叕鍏辫瘝搴撳苟鍚屾 Elasticsearch 绱㈠紩銆?*/
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

    /** 瀵煎叆鍗曚釜鍗曡瘝鍒板叕鍏辫瘝搴撱€?*/
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

    /** 閫氳繃 FreeDictionaryAPI 鑾峰彇鍗曡瘝鐨勮瘝鍏歌瘝鏉℃暟鎹€?*/
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

    /** 閫氳繃 dictionaryapi.dev 鑾峰彇鍗曡瘝鐨勯煶棰?URL銆?*/
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

    /** 浠?FreeDictionaryAPI 璇嶆潯涓彁鍙栭煶鏍囥€?*/
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

    /** 浠?FreeDictionaryAPI 璇嶆潯鐨?senses 涓彁鍙栦緥鍙ャ€?*/
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

    /** 閫掑綊鏀堕泦 senses 涓殑涓枃缈昏瘧銆?*/
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

    /** 瀵圭炕璇戝師鏂囪繘琛屾爣鍑嗗寲娓呮礂涓庡垎娈碉紝鍘婚噸鍚庡姞鍏?translations 闆嗗悎銆?*/
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

    /** 瀵逛腑鏂囬噴涔夌墖娈佃繘琛屾竻娲楋細鍘婚櫎鎷彿鍐呰嫳鏂囨敞閲娿€佹埅鏂灏鹃潪姹夊瓧瀛楃銆?*/
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

    /** 鍒ゆ柇瀛楃涓蹭腑鏄惁鍖呭惈姹夊瓧瀛楃銆?*/
    private boolean containsHanCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.UnicodeScript.of(value.charAt(index)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    /** 杩斿洖瀛楃涓蹭腑棣栦釜姹夊瓧鐨勭储寮曚綅缃€?*/
    private int firstHanIndex(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.UnicodeScript.of(value.charAt(index)) == Character.UnicodeScript.HAN) {
                return index;
            }
        }
        return -1;
    }

    /** 杩斿洖瀛楃涓蹭腑鏈€鍚庝竴涓眽瀛楃殑绱㈠紩浣嶇疆銆?*/
    private int lastHanIndex(String value) {
        for (int index = value.length() - 1; index >= 0; index--) {
            if (Character.UnicodeScript.of(value.charAt(index)) == Character.UnicodeScript.HAN) {
                return index;
            }
        }
        return -1;
    }

    /** 鍒ゆ柇瀛楃涓蹭腑鏄惁鍖呭惈鎷変竵瀛楁瘝銆?*/
    private boolean hasLatinLetter(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if ((current >= 'A' && current <= 'Z') || (current >= 'a' && current <= 'z')) {
                return true;
            }
        }
        return false;
    }

    /** 鍒ゆ柇鏄惁涓轰腑鏂囪瑷€浠ｇ爜鎴栧悕绉般€?*/
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

    /** 鏌ユ壘宸叉湁鐨勫叕鍏辫瘝鏉?ID銆?*/
    private Long findExistingPublicEntryId(String word) {
        return searchVocabularyMapper.findExistingPublicEntryId(PUBLIC_OWNER_USER_ID, PUBLIC_VISIBILITY, word);
    }

    /** 纭繚鍏叡璇嶄功瀛樺湪锛屼笉瀛樺湪鏃惰嚜鍔ㄥ垱寤恒€?*/
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

    /** 鏌ユ壘鍏叡璇嶄功 ID銆?*/
    private Long findPublicWordbookId() {
        return searchWordbookMapper.findPublicWordbookId(
                PUBLIC_OWNER_USER_ID,
                UserFacingTextNormalizer.normalizeDisplayText(PUBLIC_WORDBOOK_SOURCE)
        );
    }

    /** 鍒涘缓鍏叡璇嶆潯鍒版暟鎹簱骞惰繑鍥?ID銆?*/
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

    /** 鏇存柊鍏叡璇嶆潯鍐呭銆?*/
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

    /** 鍚屾鍏叡璇嶄功鐨勮瘝鏉¤鏁板埌 wordbooks 琛ㄣ€?*/
    private void syncPublicWordbookCount(long wordbookId) {
        searchWordbookMapper.syncWordbookCount(wordbookId);
    }

    /** 浠庢暟鎹簱璇诲彇鍏叡璇嶆潯骞跺悓姝ュ埌 Elasticsearch 绱㈠紩銆?*/
    private void indexPublicEntry(long entryId) {
        SearchDocumentVo row = searchVocabularyMapper.findDocumentById(entryId);
        if (row != null) {
            indexDocument(row);
        }
    }

    /** 鍦ㄦ寚瀹氳瘝涔︿腑鏌ユ壘鍏叡璇嶆潯 ID銆?*/
    private Long findPublicEntryId(long wordbookId, String word) {
        return searchVocabularyMapper.findPublicEntryId(PUBLIC_OWNER_USER_ID, wordbookId, word);
    }

    /** 浠庢暟鎹簱鍔犺浇璇嶆潯璇︽儏琛屻€?*/
    private DetailVo loadDetailRow(long entryId) {
        DetailVo row = searchVocabularyMapper.loadDetailRow(entryId);
        if (row == null) {
            throw new NotFoundException("Word not found");
        }
        return row;
    }

    /** 鍔犺浇鎵€鏈夎瘝姹囨潯鐩敤浜庨噸寤虹储寮曘€?*/
    private List<SearchDocumentVo> loadAllRows() {
        return searchVocabularyMapper.loadAllRows();
    }

    /** 鏍囧噯鍖栨暟鎹簱涓殑鏂囨湰鍐呭锛堣瘝姹囨潯鐩€佽瘝涔︺€佸鍏ヤ换鍔°€佸涔犵劍鐐癸級锛岃繑鍥炴洿鏂扮殑琛屾暟銆?*/
    private int normalizeDatabaseContent() {
        int updated = 0;
        updated += normalizeVocabularyEntries();
        updated += normalizeWordbooks();
        updated += normalizeImportTasks();
        updated += normalizeStudyFocusAreas();
        return updated;
    }

    /** 鏍囧噯鍖栬瘝姹囨潯鐩〃涓殑鏂囨湰鍐呭銆?*/
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

    /** 鏍囧噯鍖栬瘝涔﹁〃涓殑鏂囨湰鍐呭銆?*/
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

    /** 鏍囧噯鍖栧鍏ヤ换鍔¤〃涓殑鏂囨湰鍐呭銆?*/
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

    /** 鏍囧噯鍖栧涔犵劍鐐瑰尯鍩熺殑鏂囨湰鍐呭銆?*/
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

    /** 姣旇緝 null 瀹夊叏鐨勬枃鏈槸鍚︾浉鍚屻€?*/
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

    /** 鍒ゆ柇鍏抽敭璇嶆槸鍚﹂渶瑕佽Е鍙戝叕鍏辫瘝搴撹ˉ姘达紙hydrate锛夈€?*/
    private boolean shouldHydrate(String keyword) {
        if (keyword == null) {
            return false;
        }
        String normalized = keyword.trim();
        return !normalized.isBlank()
                && normalized.length() <= 64
                && normalized.matches("[A-Za-z][A-Za-z\\-']*");
    }

    /** 鏍囧噯鍖栨悳绱㈠叧閿瘝鐢ㄤ簬鏌ヨ銆?*/
    /** 瀵瑰師濮嬪崟璇嶅垪琛ㄨ繘琛屾爣鍑嗗寲涓庡幓閲嶃€?*/
    /** 榛樿绉嶅瓙鍗曡瘝鍒楄〃锛堝墠 100 涓父鐢ㄨ瘝锛夈€?*/
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

    /** 鏍规嵁鍗曡瘝闀垮害浼扮畻闅惧害鍒嗘暟銆?*/
    /** 鏍囧噯鍖栭煶棰?URL锛堣ˉ鍏ㄥ崗璁墠缂€锛夈€?*/
    /** 鏍囧噯鍖栭煶鏍囨枃鏈€?*/
    /** 鏍囧噯鍖栧鍏ユ潵婧愭爣璇嗐€?*/
    /** 鏍规嵁鍙鎬ф瀯寤烘潵婧愭爣绛俱€?*/
    private String buildSourceLabel(String visibility) {
        return PUBLIC_VISIBILITY.equalsIgnoreCase(visibility) ? PUBLIC_SOURCE_LABEL : PRIVATE_SOURCE_LABEL;
    }

    /** 灏嗗叕鍏辫瘝鏉″悓姝ュ埌 Elasticsearch 绱㈠紩锛堜粎鍦?ES 鍙敤鏃舵墽琛岋級銆?*/
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

    /** 纭繚绱㈠紩瀛樺湪锛屼笉瀛樺湪鍒欏垱寤猴紱澶辫触鏃舵姏鍑哄紓甯搞€?*/
    private void ensureIndex() {
        try {
            createIndex();
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException("Failed to initialize Elasticsearch index", exception);
        }
    }

    /** 灏濊瘯纭繚绱㈠紩瀛樺湪锛屽け璐ユ椂杩斿洖 false 鑰岄潪鎶涘嚭寮傚父銆?*/
    private boolean tryEnsureIndex() {
        try {
            ensureIndex();
            return true;
        } catch (RuntimeException exception) {
            log.warn("Elasticsearch is unavailable: {}", exception.getMessage());
            return false;
        }
    }

    /** 鍒犻櫎 Elasticsearch 绱㈠紩銆?*/
    private void deleteIndex() throws IOException, InterruptedException {
        sendWithoutBody("DELETE", "/" + INDEX_NAME, true);
    }

    /** 鍒涘缓 Elasticsearch 绱㈠紩骞跺畾涔夋槧灏勩€?*/
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

    /** 灏嗗崟涓?SearchDocumentVo 绱㈠紩鍒?Elasticsearch銆?*/
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

    /** 鍚?Elasticsearch 鍙戦€?JSON 璇锋眰骞惰繑鍥炲搷搴斻€?*/
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

    /** 鍚?Elasticsearch 鍙戦€佹棤 Body 璇锋眰銆?*/
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

    /** 鍒锋柊 Elasticsearch 绱㈠紩浣垮啓鍏ュ彲瑙併€?*/
    private void refreshIndex() {
        try {
            sendWithoutBody("POST", "/" + INDEX_NAME + "/_refresh", false);
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException("Failed to refresh Elasticsearch index", exception);
        }
    }

    /** 瀹夊叏鍒锋柊绱㈠紩锛屽け璐ユ椂浠呰褰曡鍛婃棩蹇椼€?*/
    private void safeRefreshIndex() {
        try {
            refreshIndex();
        } catch (RuntimeException exception) {
            log.warn("Failed to refresh Elasticsearch index after database update: {}", exception.getMessage());
        }
    }
    /** 璇嶅吀 API 杩斿洖鐨勮瘝鏉℃暟鎹€?*/
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

    /** 鎼滅储鍛戒腑鍊欓€夐」銆?*/
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

    /** 鎺掑簭鍚庣殑鎼滅储鍛戒腑缁撴灉銆?*/
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

    /** 寤鸿鍊欓€夐」銆?*/
    private record SuggestionCandidate(
            long entryId,
            String word,
            String visibility,
            Long ownerUserId,
            double score,
            MatchType matchType
    ) {
    }
    /** 瀵煎叆鎿嶄綔缁撴灉绫诲瀷銆?*/
    private enum ImportAction {
        IMPORTED,
        UPDATED,
        SKIPPED,
        FAILED
    }

    /** 鎼滅储鍖归厤绫诲瀷锛氱簿纭尮閰嶃€佸墠缂€鍖归厤銆佸寘鍚尮閰嶃€佹枃鏈尮閰嶃€?*/
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

