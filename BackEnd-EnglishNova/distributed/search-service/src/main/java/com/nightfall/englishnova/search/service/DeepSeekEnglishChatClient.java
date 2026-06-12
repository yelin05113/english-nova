package com.nightfall.englishnova.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nightfall.englishnova.search.config.ExampleEnrichmentProperties;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class DeepSeekEnglishChatClient {

    private final ObjectMapper objectMapper;
    private final ExampleEnrichmentProperties properties;
    private final HttpClient httpClient;

    public DeepSeekEnglishChatClient(
            ObjectMapper objectMapper,
            ExampleEnrichmentProperties properties
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public boolean isConfigured() {
        return missingConfigurationKeys().isEmpty();
    }

    public List<String> missingConfigurationKeys() {
        ExampleEnrichmentProperties.OpenAiProperties openai = properties.openai();
        if (openai == null) {
            return List.of("DEEPSEEK_BASE_URL", "DEEPSEEK_API_KEY", "DEEPSEEK_MODEL");
        }

        List<String> missing = new ArrayList<>();
        if (!hasText(openai.baseUrl())) {
            missing.add("DEEPSEEK_BASE_URL");
        }
        if (!hasText(openai.apiKey())) {
            missing.add("DEEPSEEK_API_KEY");
        }
        if (!hasText(openai.model())) {
            missing.add("DEEPSEEK_MODEL");
        }
        return Collections.unmodifiableList(missing);
    }

    public void streamEnglishChat(EnglishChatPayload payload, Consumer<EnglishChatStreamEvent> consumer) {
        if (!isConfigured()) {
            throw new IllegalStateException("DeepSeek English chat is not configured");
        }
        if (payload == null || !hasText(payload.userPrompt())) {
            throw new IllegalArgumentException("English chat prompt is blank");
        }

        boolean useChatCompletions = shouldUseChatCompletions();
        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(
                    useChatCompletions ? buildChatCompletionsRequestBody(payload) : buildResponsesRequestBody(payload)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize English chat request", exception);
        }

        ExampleEnrichmentProperties.OpenAiProperties openai = properties.openai();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(openai.baseUrl()) + (useChatCompletions ? "/chat/completions" : "/responses")))
                .timeout(Duration.ofSeconds(properties.resolvedChatTimeoutSeconds()))
                .header("Authorization", "Bearer " + openai.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                String errorBody = readAll(response.body());
                throw new IllegalStateException("English chat request failed: " + response.statusCode() + " " + abbreviate(errorBody, 400));
            }
            consumeEventStream(response.body(), consumer);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to call English chat model", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("English chat request interrupted", exception);
        }
    }

    private Map<String, Object> buildChatCompletionsRequestBody(EnglishChatPayload payload) {
        ExampleEnrichmentProperties.OpenAiProperties openai = properties.openai();
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", payload.systemPrompt()));
        if (hasText(payload.hiddenContextPrompt())) {
            messages.add(Map.of("role", "system", "content", payload.hiddenContextPrompt()));
        }
        for (EnglishChatTurn turn : payload.history()) {
            messages.add(Map.of("role", turn.role(), "content", turn.content()));
        }
        messages.add(Map.of("role", "user", "content", payload.userPrompt()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", openai.model());
        body.put("messages", messages);
        body.put("temperature", 0.4);
        body.put("stream", true);
        return body;
    }

    private Map<String, Object> buildResponsesRequestBody(EnglishChatPayload payload) {
        ExampleEnrichmentProperties.OpenAiProperties openai = properties.openai();
        List<Map<String, Object>> input = new ArrayList<>();
        input.add(Map.of(
                "role", "developer",
                "content", List.of(Map.of("type", "input_text", "text", payload.systemPrompt()))
        ));
        if (hasText(payload.hiddenContextPrompt())) {
            input.add(Map.of(
                    "role", "developer",
                    "content", List.of(Map.of("type", "input_text", "text", payload.hiddenContextPrompt()))
            ));
        }
        for (EnglishChatTurn turn : payload.history()) {
            input.add(Map.of(
                    "role", turn.role(),
                    "content", List.of(Map.of("type", "input_text", "text", turn.content()))
            ));
        }
        input.add(Map.of(
                "role", "user",
                "content", List.of(Map.of("type", "input_text", "text", payload.userPrompt()))
        ));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", openai.model());
        body.put("input", input);
        body.put("stream", true);
        body.put("temperature", 0.4);
        return body;
    }

    private void consumeEventStream(
            java.io.InputStream body,
            Consumer<EnglishChatStreamEvent> consumer
    ) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            String eventName = null;
            StringBuilder dataBuilder = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    emitBufferedEvent(eventName, dataBuilder.toString(), consumer);
                    eventName = null;
                    dataBuilder.setLength(0);
                    continue;
                }
                if (line.startsWith("event:")) {
                    eventName = line.substring(6).trim();
                    continue;
                }
                if (line.startsWith("data:")) {
                    if (dataBuilder.length() > 0) {
                        dataBuilder.append('\n');
                    }
                    dataBuilder.append(line.substring(5).trim());
                }
            }
            emitBufferedEvent(eventName, dataBuilder.toString(), consumer);
        }
    }

    private void emitBufferedEvent(
            String eventName,
            String data,
            Consumer<EnglishChatStreamEvent> consumer
    ) {
        if (!hasText(data)) {
            return;
        }
        if ("[DONE]".equals(data)) {
            consumer.accept(new EnglishChatStreamEvent("done", ""));
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(data);
            String token = extractToken(eventName, root);
            if (!token.isEmpty()) {
                consumer.accept(new EnglishChatStreamEvent("token", token));
            }
            String errorMessage = extractErrorMessage(eventName, root);
            if (hasText(errorMessage)) {
                consumer.accept(new EnglishChatStreamEvent("error", errorMessage));
            }
            if (isDoneEvent(eventName, root)) {
                consumer.accept(new EnglishChatStreamEvent("done", ""));
            }
        } catch (JsonProcessingException exception) {
            consumer.accept(new EnglishChatStreamEvent("error", "模型返回了无法解析的流式数据"));
            consumer.accept(new EnglishChatStreamEvent("done", ""));
        }
    }

    private String extractToken(String eventName, JsonNode root) {
        if ("response.output_text.delta".equals(eventName)) {
            return readTextNode(root.path("delta"));
        }

        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode delta = choices.get(0).path("delta");
            String content = readTextNode(delta.path("content"));
            if (hasText(content)) {
                return content;
            }
        }

        JsonNode output = root.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (!content.isArray()) {
                    continue;
                }
                for (JsonNode contentItem : content) {
                    String text = readTextNode(contentItem.path("text"));
                    if (hasText(text)) {
                        return text;
                    }
                }
            }
        }

        return "";
    }

    private String extractErrorMessage(String eventName, JsonNode root) {
        if ("error".equals(eventName)) {
            if (hasText(root.path("error").path("message").asText())) {
                return root.path("error").path("message").asText();
            }
            if (hasText(root.path("message").asText())) {
                return root.path("message").asText();
            }
        }
        return "";
    }

    private boolean isDoneEvent(String eventName, JsonNode root) {
        if ("response.completed".equals(eventName) || "done".equals(eventName)) {
            return true;
        }
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            return !choices.get(0).path("finish_reason").isMissingNode()
                    && !choices.get(0).path("finish_reason").isNull();
        }
        return false;
    }

    private boolean shouldUseChatCompletions() {
        ExampleEnrichmentProperties.OpenAiProperties openai = properties.openai();
        if (openai == null) {
            return false;
        }
        String baseUrl = openai.baseUrl() == null ? "" : openai.baseUrl().toLowerCase();
        String model = openai.model() == null ? "" : openai.model().toLowerCase();
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

    private String readTextNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText("");
        }
        if (node.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : node) {
                String nested = readTextNode(item);
                if (hasText(nested)) {
                    builder.append(nested);
                }
            }
            return builder.toString();
        }
        if (node.isObject()) {
            String nestedText = readTextNode(node.path("text"));
            if (hasText(nestedText)) {
                return nestedText;
            }
        }
        return "";
    }

    private String readAll(java.io.InputStream body) throws IOException {
        return new String(body.readAllBytes(), StandardCharsets.UTF_8);
    }

    public record EnglishChatPayload(
            String systemPrompt,
            String hiddenContextPrompt,
            List<EnglishChatTurn> history,
            String userPrompt
    ) {
    }

    public record EnglishChatTurn(
            String role,
            String content
    ) {
    }

    public record EnglishChatStreamEvent(
            String type,
            String payload
    ) {
    }
}
