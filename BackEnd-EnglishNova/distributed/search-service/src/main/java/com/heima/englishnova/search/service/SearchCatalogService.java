package com.heima.englishnova.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heima.englishnova.shared.auth.CurrentUser;
import com.heima.englishnova.shared.dto.PublicCatalogImportRequest;
import com.heima.englishnova.shared.dto.PublicCatalogImportResultDto;
import com.heima.englishnova.shared.dto.SearchHitDto;
import com.heima.englishnova.shared.dto.SearchSuggestionDto;
import com.heima.englishnova.shared.dto.WordDetailDto;
import com.heima.englishnova.shared.dto.WordSearchResponseDto;
import com.heima.englishnova.shared.events.WordbookImportedEvent;
import com.heima.englishnova.shared.exception.ForbiddenException;
import com.heima.englishnova.shared.exception.NotFoundException;
import com.heima.englishnova.shared.text.TextRepairUtils;
import com.heima.englishnova.shared.text.UserFacingTextNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SearchCatalogService {

    private static final Logger log = LoggerFactory.getLogger(SearchCatalogService.class);

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

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String elasticsearchBaseUrl;

    public SearchCatalogService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${spring.elasticsearch.uris}") String elasticsearchBaseUrl
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.elasticsearchBaseUrl = elasticsearchBaseUrl.endsWith("/")
                ? elasticsearchBaseUrl.substring(0, elasticsearchBaseUrl.length() - 1)
                : elasticsearchBaseUrl;
        this.httpClient = HttpClient.newHttpClient();
    }

    public WordSearchResponseDto searchVocabulary(String keyword, CurrentUser user) {
        String normalizedKeyword = normalizeSearchKeyword(keyword);
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

    public List<SearchSuggestionDto> searchSuggestions(String keyword, CurrentUser user) {
        String normalizedKeyword = normalizeSearchKeyword(keyword);
        if (normalizedKeyword.isBlank() || !shouldHydrate(normalizedKeyword)) {
            return List.of();
        }

        ensureIndex();
        return searchSuggestionsByWordMatch(normalizedKeyword, user);
    }

    public WordDetailDto getWordDetail(long entryId, CurrentUser user) {
        DetailRow row = loadDetailRow(entryId);
        if (PRIVATE_VISIBILITY.equalsIgnoreCase(row.visibility())
                && (user == null || row.ownerUserId() == null || row.ownerUserId() != user.id())) {
            throw new ForbiddenException("You cannot access this word");
        }

        String audioUrl = normalizeAudioUrl(row.audioUrl());
        if (audioUrl.isBlank() && shouldHydrate(row.word())) {
            audioUrl = normalizeAudioUrl(fetchAudioUrl(row.word()));
            if (!audioUrl.isBlank()) {
                jdbcTemplate.update("UPDATE vocabulary_entries SET audio_url = ? WHERE id = ?", audioUrl, entryId);
            }
        }

        return new WordDetailDto(
                row.entryId(),
                row.ownerUserId(),
                row.wordbookId(),
                UserFacingTextNormalizer.normalizeDisplayText(row.wordbookName()),
                TextRepairUtils.repair(row.word()),
                normalizePhonetic(row.phonetic()),
                UserFacingTextNormalizer.normalizeMeaningText(row.meaningCn()),
                UserFacingTextNormalizer.normalizeDisplayText(row.exampleSentence()),
                UserFacingTextNormalizer.normalizeMeaningText(row.category()),
                row.difficulty(),
                row.visibility(),
                buildSourceLabel(row.visibility()),
                UserFacingTextNormalizer.normalizeDisplayText(row.sourceName()),
                normalizeImportSource(row.importSource()),
                audioUrl
        );
    }

    public PublicCatalogImportResultDto importPublicCatalog(PublicCatalogImportRequest request) {
        boolean refreshExisting = request != null && Boolean.TRUE.equals(request.refreshExisting());
        List<String> normalizedWords = normalizeWords(request == null ? null : request.words());
        if (normalizedWords.isEmpty()) {
            normalizedWords = defaultSeedWords();
        }
        return importWords(normalizedWords, refreshExisting);
    }

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

    @RabbitListener(queues = "${english-nova.search.index-queue}")
    public void handleImportedWordbook(WordbookImportedEvent event) {
        if (!tryEnsureIndex()) {
            log.warn("Skipping Elasticsearch sync for imported wordbook {} because the cluster is unavailable", event.wordbookId());
            return;
        }
        jdbcTemplate.query(
                """
                SELECT id, user_id, visibility, wordbook_id, word, phonetic, meaning_cn, example_sentence, category, import_source
                FROM vocabulary_entries
                WHERE user_id = ? AND wordbook_id = ?
                """,
                resultSet -> {
                    while (resultSet.next()) {
                        indexDocument(mapRow(resultSet));
                    }
                    return null;
                },
                event.userId(),
                event.wordbookId()
        );
        safeRefreshIndex();
    }

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

    private List<SearchHitDto> toSearchHits(JsonNode hits, String keyword) {
        List<SearchHitCandidate> candidates = new ArrayList<>();
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            candidates.add(new SearchHitCandidate(
                    source.path("entryId").asLong(),
                    TextRepairUtils.repair(source.path("word").asText()),
                    normalizePhonetic(source.path("phonetic").asText()),
                    UserFacingTextNormalizer.normalizeMeaningText(source.path("meaningCn").asText()),
                    buildSourceLabel(source.path("visibility").asText()),
                    UserFacingTextNormalizer.normalizeDisplayText(source.path("exampleSentence").asText()),
                    UserFacingTextNormalizer.normalizeMeaningText(source.path("category").asText()),
                    source.path("visibility").asText(),
                    normalizeImportSource(source.path("importSource").asText()),
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

    private Map<String, Object> buildTextSearchQuery(String keyword) {
        return Map.of("multi_match", Map.of(
                "query", keyword,
                "fields", List.of("meaningCn^3", "category^2", "exampleSentence"),
                "type", "best_fields"
        ));
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
                    normalizePhonetic(phonetic.isBlank() ? "-" : phonetic),
                    UserFacingTextNormalizer.normalizeMeaningText(String.join(" / ", translations.stream().limit(4).toList())),
                    example.isBlank() ? "Imported from FreeDictionaryAPI.com" : UserFacingTextNormalizer.normalizeDisplayText(example),
                    partsOfSpeech.isEmpty() ? "dictionary" : UserFacingTextNormalizer.normalizeMeaningText(String.join(" / ", partsOfSpeech)),
                    scoreDifficulty(word),
                    "",
                    PUBLIC_IMPORT_SOURCE
            );
        } catch (IOException | InterruptedException exception) {
            return null;
        }
    }

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

    private boolean containsHanCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.UnicodeScript.of(value.charAt(index)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private int firstHanIndex(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.UnicodeScript.of(value.charAt(index)) == Character.UnicodeScript.HAN) {
                return index;
            }
        }
        return -1;
    }

    private int lastHanIndex(String value) {
        for (int index = value.length() - 1; index >= 0; index--) {
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

    private Long findExistingPublicEntryId(String word) {
        List<Long> ids = jdbcTemplate.query(
                """
                SELECT id
                FROM vocabulary_entries
                WHERE user_id = ? AND visibility = ? AND LOWER(word) = LOWER(?)
                ORDER BY id ASC
                LIMIT 1
                """,
                (resultSet, rowNum) -> resultSet.getLong("id"),
                PUBLIC_OWNER_USER_ID,
                PUBLIC_VISIBILITY,
                word
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private long ensurePublicWordbook() {
        Long existingId = findPublicWordbookId();
        if (existingId != null) {
            return existingId;
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO wordbooks(user_id, name, platform, source_name, import_source, word_count, created_at)
                    VALUES (?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, PUBLIC_OWNER_USER_ID);
            statement.setString(2, UserFacingTextNormalizer.normalizeDisplayText(PUBLIC_WORDBOOK_NAME));
            statement.setString(3, PUBLIC_WORDBOOK_PLATFORM);
            statement.setString(4, UserFacingTextNormalizer.normalizeDisplayText(PUBLIC_WORDBOOK_SOURCE));
            statement.setString(5, PUBLIC_IMPORT_SOURCE);
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key != null) {
            return key.longValue();
        }

        Long createdId = findPublicWordbookId();
        if (createdId != null) {
            return createdId;
        }
        throw new IllegalStateException("Failed to create public catalog wordbook");
    }

    private Long findPublicWordbookId() {
        List<Long> ids = jdbcTemplate.query(
                """
                SELECT id
                FROM wordbooks
                WHERE user_id = ? AND source_name = ?
                ORDER BY id ASC
                LIMIT 1
                """,
                (resultSet, rowNum) -> resultSet.getLong("id"),
                PUBLIC_OWNER_USER_ID,
                UserFacingTextNormalizer.normalizeDisplayText(PUBLIC_WORDBOOK_SOURCE)
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private long createPublicEntry(long wordbookId, DictionaryEntryPayload payload) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO vocabulary_entries(
                        user_id, wordbook_id, word, phonetic, meaning_cn, example_sentence, category,
                        difficulty, visibility, audio_url, import_source, created_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, PUBLIC_OWNER_USER_ID);
            statement.setLong(2, wordbookId);
            statement.setString(3, payload.word());
            statement.setString(4, payload.phonetic());
            statement.setString(5, payload.meaningCn());
            statement.setString(6, payload.exampleSentence());
            statement.setString(7, payload.category());
            statement.setInt(8, payload.difficulty());
            statement.setString(9, PUBLIC_VISIBILITY);
            statement.setString(10, payload.audioUrl());
            statement.setString(11, payload.importSource());
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key != null) {
            return key.longValue();
        }

        Long createdId = findPublicEntryId(wordbookId, payload.word());
        if (createdId != null) {
            return createdId;
        }
        throw new IllegalStateException("Failed to create public catalog entry for word: " + payload.word());
    }

    private void updatePublicEntry(long entryId, DictionaryEntryPayload payload) {
        jdbcTemplate.update(
                """
                UPDATE vocabulary_entries
                SET word = ?, phonetic = ?, meaning_cn = ?, example_sentence = ?, category = ?,
                    difficulty = ?, audio_url = ?, import_source = ?
                WHERE id = ?
                """,
                payload.word(),
                payload.phonetic(),
                payload.meaningCn(),
                payload.exampleSentence(),
                payload.category(),
                payload.difficulty(),
                payload.audioUrl(),
                payload.importSource(),
                entryId
        );
    }

    private void syncPublicWordbookCount(long wordbookId) {
        jdbcTemplate.update(
                """
                UPDATE wordbooks
                SET word_count = (
                    SELECT COUNT(*)
                    FROM vocabulary_entries
                    WHERE wordbook_id = ?
                )
                WHERE id = ?
                """,
                wordbookId,
                wordbookId
        );
    }

    private void indexPublicEntry(long entryId) {
        jdbcTemplate.query(
                """
                SELECT id, user_id, visibility, wordbook_id, word, phonetic, meaning_cn, example_sentence, category, import_source
                FROM vocabulary_entries
                WHERE id = ?
                """,
                resultSet -> {
                    while (resultSet.next()) {
                        indexDocument(mapRow(resultSet));
                    }
                    return null;
                },
                entryId
        );
    }

    private Long findPublicEntryId(long wordbookId, String word) {
        List<Long> ids = jdbcTemplate.query(
                """
                SELECT id
                FROM vocabulary_entries
                WHERE user_id = ? AND wordbook_id = ? AND LOWER(word) = LOWER(?)
                ORDER BY id DESC
                LIMIT 1
                """,
                (resultSet, rowNum) -> resultSet.getLong("id"),
                PUBLIC_OWNER_USER_ID,
                wordbookId,
                word
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private DetailRow loadDetailRow(long entryId) {
        List<DetailRow> rows = jdbcTemplate.query(
                """
                SELECT
                    v.id,
                    v.user_id,
                    v.wordbook_id,
                    w.name AS wordbook_name,
                    v.word,
                    v.phonetic,
                    v.meaning_cn,
                    v.example_sentence,
                    v.category,
                    v.difficulty,
                    v.visibility,
                    v.audio_url,
                    v.import_source,
                    w.source_name
                FROM vocabulary_entries v
                JOIN wordbooks w ON w.id = v.wordbook_id
                WHERE v.id = ?
                LIMIT 1
                """,
                (resultSet, rowNum) -> new DetailRow(
                        resultSet.getLong("id"),
                        getNullableLong(resultSet, "user_id"),
                        resultSet.getLong("wordbook_id"),
                        resultSet.getString("wordbook_name"),
                        resultSet.getString("word"),
                        resultSet.getString("phonetic"),
                        resultSet.getString("meaning_cn"),
                        resultSet.getString("example_sentence"),
                        resultSet.getString("category"),
                        resultSet.getInt("difficulty"),
                        resultSet.getString("visibility"),
                        resultSet.getString("audio_url"),
                        resultSet.getString("import_source"),
                        resultSet.getString("source_name")
                ),
                entryId
        );
        if (rows.isEmpty()) {
            throw new NotFoundException("Word not found");
        }
        return rows.get(0);
    }

    private List<SearchDocumentRow> loadAllRows() {
        return jdbcTemplate.query(
                """
                SELECT id, user_id, visibility, wordbook_id, word, phonetic, meaning_cn, example_sentence, category, import_source
                FROM vocabulary_entries
                """,
                (resultSet, rowNum) -> mapRow(resultSet)
        );
    }

    private SearchDocumentRow mapRow(ResultSet resultSet) throws SQLException {
        return new SearchDocumentRow(
                resultSet.getLong("id"),
                getNullableLong(resultSet, "user_id"),
                resultSet.getString("visibility"),
                resultSet.getLong("wordbook_id"),
                TextRepairUtils.repair(resultSet.getString("word")),
                normalizePhonetic(resultSet.getString("phonetic")),
                UserFacingTextNormalizer.normalizeMeaningText(resultSet.getString("meaning_cn")),
                UserFacingTextNormalizer.normalizeDisplayText(resultSet.getString("example_sentence")),
                UserFacingTextNormalizer.normalizeMeaningText(resultSet.getString("category")),
                normalizeImportSource(resultSet.getString("import_source"))
        );
    }

    private int normalizeDatabaseContent() {
        int updated = 0;
        updated += normalizeVocabularyEntries();
        updated += normalizeWordbooks();
        updated += normalizeImportTasks();
        updated += normalizeStudyFocusAreas();
        return updated;
    }

    private int normalizeVocabularyEntries() {
        List<VocabularyCleanupRow> rows = jdbcTemplate.query(
                """
                SELECT id, meaning_cn, example_sentence, category
                FROM vocabulary_entries
                """,
                (resultSet, rowNum) -> new VocabularyCleanupRow(
                        resultSet.getLong("id"),
                        resultSet.getString("meaning_cn"),
                        resultSet.getString("example_sentence"),
                        resultSet.getString("category")
                )
        );

        int updated = 0;
        for (VocabularyCleanupRow row : rows) {
            String meaning = UserFacingTextNormalizer.normalizeMeaningText(row.meaningCn());
            String example = UserFacingTextNormalizer.normalizeDisplayText(row.exampleSentence());
            String category = UserFacingTextNormalizer.normalizeMeaningText(row.category());
            if (!sameText(row.meaningCn(), meaning) || !sameText(row.exampleSentence(), example) || !sameText(row.category(), category)) {
                jdbcTemplate.update(
                        "UPDATE vocabulary_entries SET meaning_cn = ?, example_sentence = ?, category = ? WHERE id = ?",
                        meaning,
                        example,
                        category,
                        row.id()
                );
                updated++;
            }
        }
        return updated;
    }

    private int normalizeWordbooks() {
        List<WordbookCleanupRow> rows = jdbcTemplate.query(
                """
                SELECT id, name, source_name
                FROM wordbooks
                """,
                (resultSet, rowNum) -> new WordbookCleanupRow(
                        resultSet.getLong("id"),
                        resultSet.getString("name"),
                        resultSet.getString("source_name")
                )
        );

        int updated = 0;
        for (WordbookCleanupRow row : rows) {
            String name = UserFacingTextNormalizer.normalizeDisplayText(row.name());
            String sourceName = UserFacingTextNormalizer.normalizeDisplayText(row.sourceName());
            if (!sameText(row.name(), name) || !sameText(row.sourceName(), sourceName)) {
                jdbcTemplate.update(
                        "UPDATE wordbooks SET name = ?, source_name = ? WHERE id = ?",
                        name,
                        sourceName,
                        row.id()
                );
                updated++;
            }
        }
        return updated;
    }

    private int normalizeImportTasks() {
        List<ImportTaskCleanupRow> rows = jdbcTemplate.query(
                """
                SELECT task_id, source_name, error_message
                FROM import_tasks
                """,
                (resultSet, rowNum) -> new ImportTaskCleanupRow(
                        resultSet.getString("task_id"),
                        resultSet.getString("source_name"),
                        resultSet.getString("error_message")
                )
        );

        int updated = 0;
        for (ImportTaskCleanupRow row : rows) {
            String sourceName = UserFacingTextNormalizer.normalizeDisplayText(row.sourceName());
            String errorMessage = row.errorMessage() == null ? null : UserFacingTextNormalizer.normalizeDisplayText(row.errorMessage());
            if (!sameText(row.sourceName(), sourceName) || !sameNullableText(row.errorMessage(), errorMessage)) {
                jdbcTemplate.update(
                        "UPDATE import_tasks SET source_name = ?, error_message = ? WHERE task_id = ?",
                        sourceName,
                        errorMessage,
                        row.taskId()
                );
                updated++;
            }
        }
        return updated;
    }

    private int normalizeStudyFocusAreas() {
        List<StudyFocusCleanupRow> rows = jdbcTemplate.query(
                """
                SELECT id, focus_label
                FROM study_focus_areas
                """,
                (resultSet, rowNum) -> new StudyFocusCleanupRow(
                        resultSet.getLong("id"),
                        resultSet.getString("focus_label")
                )
        );

        int updated = 0;
        for (StudyFocusCleanupRow row : rows) {
            String focusLabel = UserFacingTextNormalizer.normalizeDisplayText(row.focusLabel());
            if (!sameText(row.focusLabel(), focusLabel)) {
                jdbcTemplate.update(
                        "UPDATE study_focus_areas SET focus_label = ? WHERE id = ?",
                        focusLabel,
                        row.id()
                );
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

    private String normalizeSearchKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }
        return UserFacingTextNormalizer.normalizeDisplayText(keyword).trim();
    }

    private List<String> normalizeWords(List<String> rawWords) {
        if (rawWords == null || rawWords.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String item : rawWords) {
            if (item == null || item.isBlank()) {
                continue;
            }
            String[] parts = item.split("[\\s,;\\uFF0C\\uFF1B]+");
            for (String part : parts) {
                String word = TextRepairUtils.repair(part).trim().toLowerCase(Locale.ROOT);
                if (word.matches("[a-z][a-z\\-']*")) {
                    normalized.add(word);
                }
                if (normalized.size() >= MAX_IMPORT_WORDS) {
                    return new ArrayList<>(normalized);
                }
            }
        }
        return new ArrayList<>(normalized);
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

    private int scoreDifficulty(String word) {
        int length = word == null ? 0 : word.trim().length();
        if (length >= 10) {
            return 5;
        }
        if (length >= 8) {
            return 4;
        }
        if (length >= 6) {
            return 3;
        }
        if (length >= 4) {
            return 2;
        }
        return 1;
    }

    private String normalizeAudioUrl(String audioUrl) {
        if (audioUrl == null || audioUrl.isBlank()) {
            return "";
        }
        String value = audioUrl.trim();
        if (value.startsWith("//")) {
            return "https:" + value;
        }
        return value;
    }

    private String normalizePhonetic(String phonetic) {
        if (phonetic == null) {
            return "";
        }
        return phonetic.trim();
    }

    private String normalizeImportSource(String importSource) {
        if (importSource == null || importSource.isBlank()) {
            return "unknown";
        }
        return TextRepairUtils.repair(importSource).trim().toLowerCase(Locale.ROOT);
    }

    private String buildSourceLabel(String visibility) {
        return PUBLIC_VISIBILITY.equalsIgnoreCase(visibility) ? PUBLIC_SOURCE_LABEL : PRIVATE_SOURCE_LABEL;
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

    private void ensureIndex() {
        try {
            createIndex();
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException("Failed to initialize Elasticsearch index", exception);
        }
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

    private void indexDocument(SearchDocumentRow row) {
        try {
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("entryId", row.entryId());
            document.put("ownerUserId", row.ownerUserId());
            document.put("visibility", row.visibility());
            document.put("wordbookId", row.wordbookId());
            document.put("word", row.word());
            document.put("wordExact", normalizeIndexedWord(row.word()));
            document.put("wordWildcard", normalizeIndexedWord(row.word()));
            document.put("wordSuggest", row.word());
            document.put("phonetic", row.phonetic());
            document.put("meaningCn", row.meaningCn());
            document.put("exampleSentence", row.exampleSentence());
            document.put("category", row.category());
            document.put("importSource", row.importSource());
            sendJson("PUT", "/" + INDEX_NAME + "/_doc/" + row.entryId(), document, false);
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

    private Long getNullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private record SearchDocumentRow(
            long entryId,
            Long ownerUserId,
            String visibility,
            long wordbookId,
            String word,
            String phonetic,
            String meaningCn,
            String exampleSentence,
            String category,
            String importSource
    ) {
    }

    private record DetailRow(
            long entryId,
            Long ownerUserId,
            long wordbookId,
            String wordbookName,
            String word,
            String phonetic,
            String meaningCn,
            String exampleSentence,
            String category,
            int difficulty,
            String visibility,
            String audioUrl,
            String importSource,
            String sourceName
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

    private record SuggestionCandidate(
            long entryId,
            String word,
            String visibility,
            Long ownerUserId,
            double score,
            MatchType matchType
    ) {
    }

    private record VocabularyCleanupRow(
            long id,
            String meaningCn,
            String exampleSentence,
            String category
    ) {
    }

    private record WordbookCleanupRow(
            long id,
            String name,
            String sourceName
    ) {
    }

    private record ImportTaskCleanupRow(
            String taskId,
            String sourceName,
            String errorMessage
    ) {
    }

    private record StudyFocusCleanupRow(
            long id,
            String focusLabel
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
