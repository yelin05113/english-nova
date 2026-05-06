package com.nightfall.englishnova.search.service;

import com.nightfall.englishnova.search.config.ExampleEnrichmentProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class ExampleAudioStorageService {

    private final Path audioDirectory;

    public ExampleAudioStorageService(ExampleEnrichmentProperties properties) {
        this.audioDirectory = resolveStorageRoot(properties.resolvedExampleAudioDir()).normalize();
    }

    public String publicExampleAudioUrl(long entryId) {
        return "/search/example-audio/" + entryId;
    }

    public Path resolvePublicExampleAudioFile(long entryId, String correctedEnglish) {
        return audioDirectory.resolve(buildPublicExampleAudioFileName(entryId, correctedEnglish)).normalize();
    }

    public void storePublicExampleAudio(long entryId, String correctedEnglish, byte[] payload) {
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("Example audio payload is empty");
        }
        Path target = resolvePublicExampleAudioFile(entryId, correctedEnglish);
        ensureInsideAudioDirectory(target);
        try {
            Files.createDirectories(audioDirectory);
            Files.write(target, payload);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store generated example audio", exception);
        }
    }

    public byte[] loadPublicExampleAudio(long entryId, String correctedEnglish) {
        Path target = resolvePublicExampleAudioFile(entryId, correctedEnglish);
        ensureInsideAudioDirectory(target);
        try {
            return Files.readAllBytes(target);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read generated example audio", exception);
        }
    }

    public boolean hasPublicExampleAudio(long entryId, String correctedEnglish) {
        if (correctedEnglish == null || correctedEnglish.isBlank()) {
            return false;
        }
        Path target = resolvePublicExampleAudioFile(entryId, correctedEnglish);
        ensureInsideAudioDirectory(target);
        return Files.isRegularFile(target);
    }

    private Path resolveStorageRoot(String configuredDirectory) {
        Path configuredPath = Path.of(configuredDirectory);
        if (configuredPath.isAbsolute()) {
            return configuredPath;
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

    private String buildPublicExampleAudioFileName(long entryId, String correctedEnglish) {
        return "public-entry-" + entryId + "-" + stableHash(correctedEnglish) + ".mp3";
    }

    private String stableHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < 8 && index < bytes.length; index++) {
                builder.append(String.format("%02x", bytes[index]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void ensureInsideAudioDirectory(Path target) {
        if (!target.startsWith(audioDirectory)) {
            throw new IllegalArgumentException("Invalid generated example audio path");
        }
    }
}
