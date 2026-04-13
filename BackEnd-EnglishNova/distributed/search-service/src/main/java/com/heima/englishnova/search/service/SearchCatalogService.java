package com.heima.englishnova.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heima.englishnova.shared.auth.CurrentUser;
import com.heima.englishnova.shared.dto.SearchHitDto;
import com.heima.englishnova.shared.dto.WordSearchResponseDto;
import com.heima.englishnova.shared.events.WordbookImportedEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SearchCatalogService {

    private static final String INDEX_NAME = "english-nova-words";

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
        ensureIndex();
        List<SearchHitDto> publicHits = searchByScope(keyword, null, "PUBLIC");
        List<SearchHitDto> myHits = user == null ? List.of() : searchByScope(keyword, user.id(), "PRIVATE");
        return new WordSearchResponseDto(publicHits, myHits);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuildAll() {
        try {
            deleteIndex();
            createIndex();
            List<SearchDocumentRow> rows = jdbcTemplate.query(
                    """
                    SELECT id, user_id, visibility, wordbook_id, word, phonetic, meaning_cn, example_sentence, category
                    FROM vocabulary_entries
                    """,
                    (resultSet, rowNum) -> new SearchDocumentRow(
                            resultSet.getLong("id"),
                            getNullableLong(resultSet, "user_id"),
                            resultSet.getString("visibility"),
                            resultSet.getLong("wordbook_id"),
                            resultSet.getString("word"),
                            resultSet.getString("phonetic"),
                            resultSet.getString("meaning_cn"),
                            resultSet.getString("example_sentence"),
                            resultSet.getString("category")
                    )
            );
            rows.forEach(this::indexDocument);
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException("重建 Elasticsearch 索引失败", exception);
        }
    }

    @RabbitListener(queues = "${english-nova.search.index-queue}")
    public void handleImportedWordbook(WordbookImportedEvent event) {
        ensureIndex();
        List<SearchDocumentRow> rows = jdbcTemplate.query(
                """
                SELECT id, user_id, visibility, wordbook_id, word, phonetic, meaning_cn, example_sentence, category
                FROM vocabulary_entries
                WHERE user_id = ? AND wordbook_id = ?
                """,
                (resultSet, rowNum) -> new SearchDocumentRow(
                        resultSet.getLong("id"),
                        getNullableLong(resultSet, "user_id"),
                        resultSet.getString("visibility"),
                        resultSet.getLong("wordbook_id"),
                        resultSet.getString("word"),
                        resultSet.getString("phonetic"),
                        resultSet.getString("meaning_cn"),
                        resultSet.getString("example_sentence"),
                        resultSet.getString("category")
                ),
                event.userId(),
                event.wordbookId()
        );
        rows.forEach(this::indexDocument);
    }

    private List<SearchHitDto> searchByScope(String keyword, Long ownerUserId, String visibility) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("size", keyword == null || keyword.isBlank() ? 8 : 12);

            List<Object> filters = new ArrayList<>();
            filters.add(Map.of("term", Map.of("visibility", visibility)));
            if (ownerUserId != null) {
                filters.add(Map.of("term", Map.of("ownerUserId", ownerUserId)));
            }

            Object mustQuery = keyword == null || keyword.isBlank()
                    ? Map.of("match_all", Map.of())
                    : Map.of("multi_match", Map.of(
                    "query", keyword,
                    "fields", List.of("word^3", "meaningCn^2", "exampleSentence", "category")
            ));

            body.put("query", Map.of(
                    "bool", Map.of(
                            "filter", filters,
                            "must", List.of(mustQuery)
                    )
            ));

            JsonNode response = sendJson("POST", "/" + INDEX_NAME + "/_search", body, false);
            JsonNode hits = response.path("hits").path("hits");
            List<SearchHitDto> result = new ArrayList<>();
            for (JsonNode hit : hits) {
                JsonNode source = hit.path("_source");
                result.add(new SearchHitDto(
                        source.path("entryId").asLong(),
                        source.path("word").asText(),
                        source.path("phonetic").asText(),
                        source.path("meaningCn").asText(),
                        "PUBLIC".equals(source.path("visibility").asText()) ? "公共词库" : "我的词库",
                        source.path("exampleSentence").asText(),
                        source.path("category").asText(),
                        source.path("visibility").asText()
                ));
            }
            return result;
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException("执行 Elasticsearch 查询失败", exception);
        }
    }

    private void ensureIndex() {
        try {
            createIndex();
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException("初始化 Elasticsearch 索引失败", exception);
        }
    }

    private void deleteIndex() throws IOException, InterruptedException {
        sendWithoutBody("DELETE", "/" + INDEX_NAME, true);
    }

    private void createIndex() throws IOException, InterruptedException {
        Map<String, Object> mappings = Map.of(
                "mappings", Map.of(
                        "properties", Map.of(
                                "entryId", Map.of("type", "long"),
                                "ownerUserId", Map.of("type", "long"),
                                "visibility", Map.of("type", "keyword"),
                                "wordbookId", Map.of("type", "long"),
                                "word", Map.of("type", "text"),
                                "phonetic", Map.of("type", "text"),
                                "meaningCn", Map.of("type", "text"),
                                "exampleSentence", Map.of("type", "text"),
                                "category", Map.of("type", "text")
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
            document.put("phonetic", row.phonetic());
            document.put("meaningCn", row.meaningCn());
            document.put("exampleSentence", row.exampleSentence());
            document.put("category", row.category());
            sendJson("PUT", "/" + INDEX_NAME + "/_doc/" + row.entryId(), document, false);
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException("写入 Elasticsearch 文档失败", exception);
        }
    }

    private JsonNode sendJson(String method, String path, Object body, boolean ignoreBadRequest) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(elasticsearchBaseUrl + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400 && !(ignoreNotFound && response.statusCode() == 404)) {
            throw new IllegalStateException("Elasticsearch request failed: " + response.statusCode() + " " + response.body());
        }
    }

    private Long getNullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
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
            String category
    ) {
    }
}
