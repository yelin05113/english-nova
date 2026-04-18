package com.nightfall.englishnova.importservice.utools;

import com.nightfall.englishnova.shared.enums.WordImportPlatform;
import com.nightfall.englishnova.shared.text.TextRepairUtils;
import com.nightfall.englishnova.shared.text.UserFacingTextNormalizer;

import java.util.Locale;

public final class ImportTaskUtools {

    private ImportTaskUtools() {
    }

    public static String resolveSourceName(String sourceName, String originalFileName) {
        if (sourceName != null && !sourceName.isBlank()) {
            return truncate(UserFacingTextNormalizer.normalizeDisplayText(sourceName), 120);
        }
        if (originalFileName != null && !originalFileName.isBlank()) {
            String normalized = originalFileName.trim();
            int dotIndex = normalized.lastIndexOf('.');
            if (dotIndex > 0) {
                normalized = normalized.substring(0, dotIndex);
            }
            return truncate(UserFacingTextNormalizer.normalizeDisplayText(normalized), 120);
        }
        return "anki-import";
    }

    public static String safeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "upload.bin";
        }
        return originalFileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public static String defaultText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return TextRepairUtils.repair(fallback);
        }
        return TextRepairUtils.repair(value);
    }

    public static String resolveImportSource(WordImportPlatform platform) {
        return platform.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
