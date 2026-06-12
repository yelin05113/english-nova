package com.nightfall.englishnova.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nightfall.englishnova.search.config.ExampleEnrichmentProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiExampleEnrichmentClientTest {

    @SuppressWarnings("unchecked")
    @Test
    void mimoTextUsesChatCompletions() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"choices":[{"message":{"content":"{\\"items\\":[{\\"entryType\\":\\"USER\\",\\"entryId\\":1,\\"correctedEnglish\\":\\"This is a test.\\",\\"chineseSentence\\":\\"这是一个测试。\\"}]}"}}]}
                """);

        HttpRequest[] capturedRequest = new HttpRequest[1];
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            capturedRequest[0] = invocation.getArgument(0);
            return response;
        });

        OpenAiExampleEnrichmentClient client = new OpenAiExampleEnrichmentClient(
                new ObjectMapper(),
                mimoProperties(),
                httpClient
        );

        OpenAiExampleEnrichmentClient.UserExampleEnrichmentResult result = client.enrichUserExamples(
                java.util.List.of(new OpenAiExampleEnrichmentClient.UserExampleEnrichmentRequest("USER", 1L, "this are test"))
        ).get(0);

        assertEquals("USER", result.entryType());
        assertEquals(1L, result.entryId());
        assertEquals("This is a test.", result.correctedEnglish());
        assertTrue(capturedRequest[0].uri().toString().endsWith("/chat/completions"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void mimoSpeechUsesChatCompletionsAudioPayload() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        byte[] expectedAudio = "mp3-data".getBytes(StandardCharsets.UTF_8);
        String audioBase64 = Base64.getEncoder().encodeToString(expectedAudio);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"choices":[{"message":{"content":"","audio":{"data":"%s"}}}]}
                """.formatted(audioBase64));

        HttpRequest[] capturedRequest = new HttpRequest[1];
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            capturedRequest[0] = invocation.getArgument(0);
            return response;
        });

        OpenAiExampleEnrichmentClient client = new OpenAiExampleEnrichmentClient(
                new ObjectMapper(),
                mimoProperties(),
                httpClient
        );

        byte[] actualAudio = client.synthesizeSpeech("This is a test.");

        assertArrayEquals(expectedAudio, actualAudio);
        assertEquals(URI.create("https://api.xiaomimimo.com/v1/chat/completions"), capturedRequest[0].uri());
    }

    private ExampleEnrichmentProperties mimoProperties() {
        return new ExampleEnrichmentProperties(
                new ExampleEnrichmentProperties.OpenAiProperties(
                        "https://api.xiaomimimo.com/v1",
                        "sk-test",
                        "mimo-v2.5",
                        "mimo-v2-tts",
                        "default_en"
                ),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
