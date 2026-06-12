package com.nightfall.englishnova.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nightfall.englishnova.search.config.ExampleEnrichmentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiExampleEnrichmentClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final Logger log = LoggerFactory.getLogger(OpenAiExampleEnrichmentClient.class);

    private final ObjectMapper objectMapper;
    private final ExampleEnrichmentProperties properties;
    private final HttpClient httpClient;

    @Autowired
    public OpenAiExampleEnrichmentClient(
            ObjectMapper objectMapper,
            ExampleEnrichmentProperties properties
    ) {
        this(
                objectMapper,
                properties,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .build()
        );
    }

    OpenAiExampleEnrichmentClient(
            ObjectMapper objectMapper,
            ExampleEnrichmentProperties properties,
            HttpClient httpClient
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = httpClient;
    }

    public boolean isConfigured() {
        return isTextConfigured();
    }

    public boolean isTextConfigured() {
        ExampleEnrichmentProperties.OpenAiProperties openai = properties.openai();
        return openai != null
                && hasText(openai.baseUrl())
                && hasText(openai.apiKey())
                && hasText(openai.model());
    }

    public boolean isSpeechConfigured() {
        ExampleEnrichmentProperties.OpenAiProperties openai = properties.openai();
        return openai != null
                && hasText(openai.baseUrl())
                && hasText(openai.apiKey())
                && hasText(openai.ttsModel())
                && hasText(openai.ttsVoice());
    }

    public List<UserExampleEnrichmentResult> enrichUserExamples(List<UserExampleEnrichmentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        if (!isTextConfigured()) {
            throw new IllegalStateException("OpenAI text enrichment is not configured");
        }

        String requestJson;
        boolean useChatCompletions = shouldUseChatCompletions();
        try {
            requestJson = objectMapper.writeValueAsString(useChatCompletions
                    ? buildCorrectionChatRequestBody(requests)
                    : buildCorrectionRequestBody(requests));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize OpenAI enrichment request", exception);
        }

        try {
            JsonNode items = executeStructuredJsonRequest(
                    requestJson,
                    useChatCompletions,
                    "user-example-enrichment",
                    requests.size(),
                    properties.openai() == null ? "" : properties.openai().model()
            );
            List<UserExampleEnrichmentResult> results = new ArrayList<>();
            for (JsonNode item : items) {
                results.add(new UserExampleEnrichmentResult(
                        item.path("entryType").asText(),
                        item.path("entryId").asLong(),
                        item.path("correctedEnglish").asText(),
                        item.path("chineseSentence").asText()
                ));
            }
            return results;
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException("Failed to call OpenAI example enrichment", exception);
        }
    }

    public List<PublicExampleGenerationResult> generatePublicExamples(List<PublicExampleGenerationRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        if (!isTextConfigured()) {
            throw new IllegalStateException("OpenAI text enrichment is not configured");
        }

        String requestJson;
        boolean useChatCompletions = shouldUseChatCompletions();
        try {
            requestJson = objectMapper.writeValueAsString(useChatCompletions
                    ? buildPublicGenerationChatRequestBody(requests)
                    : buildPublicGenerationRequestBody(requests));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize OpenAI enrichment request", exception);
        }

        try {
            JsonNode items = executeStructuredJsonRequest(
                    requestJson,
                    useChatCompletions,
                    "public-example-generation",
                    requests.size(),
                    properties.openai() == null ? "" : properties.openai().model()
            );
            List<PublicExampleGenerationResult> results = new ArrayList<>();
            for (JsonNode item : items) {
                results.add(new PublicExampleGenerationResult(
                        item.path("entryType").asText(),
                        item.path("entryId").asLong(),
                        item.path("correctedEnglish").asText(),
                        item.path("chineseSentence").asText()
                ));
            }
            return results;
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException("Failed to call OpenAI public example generation", exception);
        }
    }

    public byte[] synthesizeSpeech(String input) {
        if (!isSpeechConfigured()) {
            throw new IllegalStateException("OpenAI speech synthesis is not configured");
        }
        if (!hasText(input)) {
            throw new IllegalArgumentException("Speech input is blank");
        }

        ExampleEnrichmentProperties.OpenAiProperties openai = properties.openai();
        if (shouldUseChatCompletionsForSpeech()) {
            return synthesizeSpeechWithChatCompletions(openai, input);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", openai.ttsModel());
        body.put("voice", openai.ttsVoice());
        body.put("input", input);
        body.put("response_format", "mp3");
        body.put("instructions", "Speak clearly at a moderate pace for English learners.");

        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize OpenAI speech request", exception);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(openai.baseUrl()) + "/audio/speech"))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + openai.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "audio/mpeg")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("OpenAI speech request failed: " + response.statusCode() + " " + abbreviate(new String(response.body(), StandardCharsets.UTF_8), 400));
            }
            if (response.body() == null || response.body().length == 0) {
                throw new IllegalStateException("OpenAI speech response was empty");
            }
            return response.body();
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException("Failed to call OpenAI speech synthesis", exception);
        }
    }

    private byte[] synthesizeSpeechWithChatCompletions(
            ExampleEnrichmentProperties.OpenAiProperties openai,
            String input
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", openai.ttsModel());
        body.put("messages", List.of(Map.of(
                "role", "assistant",
                "content", input
        )));
        body.put("audio", Map.of(
                "voice", openai.ttsVoice(),
                "format", "mp3"
        ));

        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize OpenAI speech request", exception);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(openai.baseUrl()) + "/chat/completions"))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + openai.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("OpenAI speech request failed: " + response.statusCode() + " " + abbreviate(response.body(), 400));
            }
            JsonNode root = objectMapper.readTree(response.body());
            String audioBase64 = extractChatCompletionAudioPayload(root);
            if (!hasText(audioBase64)) {
                throw new IllegalStateException("OpenAI speech response did not contain audio data");
            }
            try {
                return Base64.getDecoder().decode(audioBase64);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("OpenAI speech response contained invalid audio data", exception);
            }
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException("Failed to call OpenAI speech synthesis", exception);
        }
    }

    private JsonNode executeStructuredJsonRequest(
            String requestJson,
            boolean useChatCompletions,
            String requestType,
            int itemCount,
            String model
    ) throws IOException, InterruptedException {
        ExampleEnrichmentProperties.OpenAiProperties openai = properties.openai();
        String endpoint = useChatCompletions ? "/chat/completions" : "/responses";
        long startedAt = System.nanoTime();
        int payloadBytes = requestJson.getBytes(StandardCharsets.UTF_8).length;
        log.info(
                "OpenAI {} request starting: endpoint={}, model={}, itemCount={}, payloadBytes={}",
                requestType,
                endpoint,
                model,
                itemCount,
                payloadBytes
        );
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(openai.baseUrl()) + endpoint))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + openai.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException exception) {
            log.warn(
                    "OpenAI {} request transport failed after {} ms: endpoint={}, model={}, itemCount={}",
                    requestType,
                    elapsedMillis(startedAt),
                    endpoint,
                    model,
                    itemCount,
                    exception
            );
            throw exception;
        }
        log.info(
                "OpenAI {} response received: endpoint={}, model={}, itemCount={}, status={}, elapsedMs={}, bodyChars={}",
                requestType,
                endpoint,
                model,
                itemCount,
                response.statusCode(),
                elapsedMillis(startedAt),
                response.body() == null ? 0 : response.body().length()
        );
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Text enrichment request failed: " + response.statusCode() + " " + abbreviate(response.body(), 400));
        }

        try {
            JsonNode root = objectMapper.readTree(response.body());
            String jsonPayload = useChatCompletions ? extractChatCompletionJsonPayload(root) : extractJsonPayload(root);
            if (!hasText(jsonPayload)) {
                throw new IllegalStateException("OpenAI response did not contain structured output");
            }

            JsonNode parsed = objectMapper.readTree(jsonPayload);
            JsonNode items = parsed.path("items");
            if (!items.isArray()) {
                throw new IllegalStateException("OpenAI response JSON did not contain an items array");
            }
            log.info(
                    "OpenAI {} response parsed: endpoint={}, model={}, itemCount={}, parsedItems={}, elapsedMs={}",
                    requestType,
                    endpoint,
                    model,
                    itemCount,
                    items.size(),
                    elapsedMillis(startedAt)
            );
            return items;
        } catch (IOException | RuntimeException exception) {
            log.warn(
                    "OpenAI {} response parse failed after {} ms: endpoint={}, model={}, itemCount={}",
                    requestType,
                    elapsedMillis(startedAt),
                    endpoint,
                    model,
                    itemCount,
                    exception
            );
            throw exception;
        }
    }

    private Map<String, Object> buildCorrectionRequestBody(List<UserExampleEnrichmentRequest> requests) {
        ExampleEnrichmentProperties.OpenAiProperties openai = properties.openai();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", openai.model());
        body.put("input", List.of(
                Map.of(
                        "role", "developer",
                        "content", List.of(Map.of(
                                "type", "input_text",
                                "text", """
                                        You correct ungrammatical English example sentences and translate them into natural Simplified Chinese.
                                        Return structured JSON only.
                                        Keep identifiers unchanged.
                                        Make the minimum necessary correction to the English.
                                        Translate from the corrected English into fluent natural Chinese.
                                        Do not add explanations or extra fields.
                                        """
                        ))
                ),
                Map.of(
                        "role", "user",
                        "content", List.of(Map.of(
                                "type", "input_text",
                                "text", buildUserPrompt(requests, """
                                        Process every item in this JSON array and return one result for each item.
                                        Input items:
                                        %s
                                        """)
                        ))
                )
        ));
        body.put("text", Map.of("format", buildJsonSchemaFormat()));
        return body;
    }

    private Map<String, Object> buildPublicGenerationRequestBody(List<PublicExampleGenerationRequest> requests) {
        ExampleEnrichmentProperties.OpenAiProperties openai = properties.openai();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", openai.model());
        body.put("input", List.of(
                Map.of(
                        "role", "developer",
                        "content", List.of(Map.of(
                                "type", "input_text",
                                "text", """
                                        You create one natural English example sentence for each vocabulary item and translate it into natural Simplified Chinese.
                                        Keep identifiers unchanged.
                                        Use the main Chinese meaning as the primary sense.
                                        correctedEnglish must be a complete English sentence, not a word, phrase, title, or definition.
                                        correctedEnglish should be 6 to 18 words, have normal sentence structure, and contain the target word naturally.
                                        chineseSentence must translate correctedEnglish, not merely explain the vocabulary word.
                                        For names, places, and proper nouns, still write a complete sentence.
                                        Use the optional seedExampleSentence only as loose context and do not copy low-quality wording.
                                        Return structured JSON only.
                                        Do not add explanations or extra fields.
                                        """
                        ))
                ),
                Map.of(
                        "role", "user",
                        "content", List.of(Map.of(
                                "type", "input_text",
                                "text", buildUserPrompt(requests, """
                                        Process every item in this JSON array and generate one result for each item.
                                        Input items:
                                        %s
                                        """)
                        ))
                )
        ));
        body.put("text", Map.of("format", buildJsonSchemaFormat()));
        return body;
    }

    private Map<String, Object> buildCorrectionChatRequestBody(List<UserExampleEnrichmentRequest> requests) {
        ExampleEnrichmentProperties.OpenAiProperties openai = properties.openai();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", openai.model());
        body.put("messages", List.of(
                Map.of(
                        "role", "system",
                        "content", """
                                You correct ungrammatical English example sentences and translate them into natural Simplified Chinese.
                                Return a JSON object only, with an items array.
                                Keep identifiers unchanged.
                                Make the minimum necessary correction to the English.
                                Translate from the corrected English into fluent natural Chinese.
                                Do not add explanations or extra fields.
                                """
                ),
                Map.of(
                        "role", "user",
                        "content", buildUserPrompt(requests, """
                                Process every item in this JSON array and return one result for each item.
                                Output JSON shape:
                                {"items":[{"entryType":"USER","entryId":1,"correctedEnglish":"...","chineseSentence":"..."}]}
                                Input items:
                                %s
                                """)
                )
        ));
        body.put("response_format", Map.of("type", "json_object"));
        body.put("temperature", 0.2);
        return body;
    }

    private Map<String, Object> buildPublicGenerationChatRequestBody(List<PublicExampleGenerationRequest> requests) {
        ExampleEnrichmentProperties.OpenAiProperties openai = properties.openai();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", openai.model());
        body.put("messages", List.of(
                Map.of(
                        "role", "system",
                        "content", """
                                You create one natural English example sentence for each vocabulary item and translate it into natural Simplified Chinese.
                                Return a JSON object only, with an items array.
                                Keep identifiers unchanged.
                                Use the main Chinese meaning as the primary sense.
                                correctedEnglish must be a complete English sentence, not a word, phrase, title, or definition.
                                correctedEnglish should be 6 to 18 words, have normal sentence structure, and contain the target word naturally.
                                chineseSentence must translate correctedEnglish, not merely explain the vocabulary word.
                                For names, places, and proper nouns, still write a complete sentence.
                                Use the optional seedExampleSentence only as loose context and do not copy low-quality wording.
                                Do not add explanations or extra fields.
                                """
                ),
                Map.of(
                        "role", "user",
                        "content", buildUserPrompt(requests, """
                                Process every item in this JSON array and generate one result for each item.
                                Output JSON shape:
                                {"items":[{"entryType":"PUBLIC","entryId":1,"correctedEnglish":"Jacqueline smiled when she heard the news.","chineseSentence":"杰奎琳听到这个消息时笑了。"}]}
                                Input items:
                                %s
                                """)
                )
        ));
        body.put("response_format", Map.of("type", "json_object"));
        body.put("temperature", 0.4);
        return body;
    }

    private String buildUserPrompt(Object requests, String template) {
        try {
            return template.formatted(objectMapper.writeValueAsString(requests));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize enrichment prompt payload", exception);
        }
    }

    private Map<String, Object> buildJsonSchemaFormat() {
        Map<String, Object> itemSchema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "entryType", Map.of("type", "string"),
                        "entryId", Map.of("type", "integer"),
                        "correctedEnglish", Map.of("type", "string"),
                        "chineseSentence", Map.of("type", "string")
                ),
                "required", List.of("entryType", "entryId", "correctedEnglish", "chineseSentence")
        );

        Map<String, Object> rootSchema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "items", Map.of(
                                "type", "array",
                                "items", itemSchema
                        )
                ),
                "required", List.of("items")
        );

        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", "example_sentence_enrichment_batch");
        format.put("strict", true);
        format.put("schema", rootSchema);
        return format;
    }

    private String extractJsonPayload(JsonNode root) {
        String outputText = root.path("output_text").asText();
        if (hasText(outputText)) {
            return outputText;
        }

        JsonNode output = root.path("output");
        if (!output.isArray()) {
            return "";
        }
        for (JsonNode message : output) {
            JsonNode content = message.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode item : content) {
                if (hasText(item.path("text").asText())) {
                    return item.path("text").asText();
                }
                JsonNode json = item.path("json");
                if (!json.isMissingNode() && !json.isNull()) {
                    return json.toString();
                }
            }
        }
        return "";
    }

    private String extractChatCompletionJsonPayload(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return "";
        }
        return choices.get(0).path("message").path("content").asText();
    }

    private String extractChatCompletionAudioPayload(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return "";
        }
        return choices.get(0).path("message").path("audio").path("data").asText();
    }

    private boolean shouldUseChatCompletions() {
        ExampleEnrichmentProperties.OpenAiProperties openai = properties.openai();
        if (openai == null) {
            return false;
        }
        return isChatCompletionsPreferred(openai.baseUrl(), openai.model());
    }

    private boolean shouldUseChatCompletionsForSpeech() {
        ExampleEnrichmentProperties.OpenAiProperties openai = properties.openai();
        if (openai == null) {
            return false;
        }
        return isChatCompletionsPreferred(openai.baseUrl(), openai.ttsModel());
    }

    private boolean isChatCompletionsPreferred(String baseUrlValue, String modelValue) {
        String baseUrl = baseUrlValue == null ? "" : baseUrlValue.toLowerCase();
        String model = modelValue == null ? "" : modelValue.toLowerCase();
        return baseUrl.contains("deepseek")
                || baseUrl.contains("xiaomimimo.com")
                || model.startsWith("deepseek")
                || model.startsWith("mimo-");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    public record UserExampleEnrichmentRequest(
            String entryType,
            long entryId,
            String originalEnglish
    ) {
    }

    public record PublicExampleGenerationRequest(
            String entryType,
            long entryId,
            String word,
            String meaningCn,
            String category,
            String seedExampleSentence
    ) {
    }

    public record UserExampleEnrichmentResult(
            String entryType,
            long entryId,
            String correctedEnglish,
            String chineseSentence
    ) {
    }

    public record PublicExampleGenerationResult(
            String entryType,
            long entryId,
            String correctedEnglish,
            String chineseSentence
    ) {
    }
}
