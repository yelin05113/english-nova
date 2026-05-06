package com.nightfall.englishnova.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "english-nova.enrichment")
public record ExampleEnrichmentProperties(
        OpenAiProperties openai,
        Integer batchSize,
        Integer workerConcurrency,
        Integer ttsConcurrency,
        Integer maxRetries,
        Long workerDelayMs,
        Long backfillDelayMs,
        Integer publicLimit,
        Integer chatMaxTurns,
        Long chatTimeoutSeconds,
        Integer chatMaxOutputChars,
        Boolean exampleAudioEnabled,
        String exampleAudioDir
) {

    public int resolvedBatchSize() {
        return batchSize == null || batchSize <= 0 ? 30 : batchSize;
    }

    public int resolvedWorkerConcurrency() {
        return workerConcurrency == null || workerConcurrency <= 0 ? 12 : workerConcurrency;
    }

    public int resolvedTtsConcurrency() {
        return ttsConcurrency == null || ttsConcurrency <= 0 ? 6 : ttsConcurrency;
    }

    public int resolvedMaxRetries() {
        return maxRetries == null || maxRetries <= 0 ? 3 : maxRetries;
    }

    public long resolvedWorkerDelayMs() {
        return workerDelayMs == null || workerDelayMs < 0 ? 1000L : workerDelayMs;
    }

    public long resolvedBackfillDelayMs() {
        return backfillDelayMs == null || backfillDelayMs < 0 ? 10000L : backfillDelayMs;
    }

    public int resolvedPublicLimit() {
        return publicLimit == null || publicLimit <= 0 ? 35000 : publicLimit;
    }

    public int resolvedChatMaxTurns() {
        return chatMaxTurns == null || chatMaxTurns <= 0 ? 5 : chatMaxTurns;
    }

    public long resolvedChatTimeoutSeconds() {
        return chatTimeoutSeconds == null || chatTimeoutSeconds <= 0 ? 60L : chatTimeoutSeconds;
    }

    public int resolvedChatMaxOutputChars() {
        return chatMaxOutputChars == null || chatMaxOutputChars <= 0 ? 1800 : chatMaxOutputChars;
    }

    public boolean resolvedExampleAudioEnabled() {
        return Boolean.TRUE.equals(exampleAudioEnabled);
    }

    public String resolvedExampleAudioDir() {
        return exampleAudioDir == null || exampleAudioDir.isBlank()
                ? "upload/example-audio"
                : exampleAudioDir.trim();
    }

    public record OpenAiProperties(
            String baseUrl,
            String apiKey,
            String model,
            String ttsModel,
            String ttsVoice
    ) {
    }
}
