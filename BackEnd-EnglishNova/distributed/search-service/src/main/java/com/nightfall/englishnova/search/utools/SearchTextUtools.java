package com.nightfall.englishnova.search.utools;

import com.nightfall.englishnova.shared.text.TextRepairUtils;
import com.nightfall.englishnova.shared.text.UserFacingTextNormalizer;
import com.nightfall.englishnova.shared.text.PhoneticNormalizer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class SearchTextUtools {

    private SearchTextUtools() {
    }

    public static String normalizeSearchKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }
        return UserFacingTextNormalizer.normalizeDisplayText(keyword).trim();
    }

    public static List<String> normalizeWords(List<String> rawWords, int maxWords) {
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
                if (normalized.size() >= maxWords) {
                    return new ArrayList<>(normalized);
                }
            }
        }
        return new ArrayList<>(normalized);
    }

    public static int scoreDifficulty(String word) {
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

    public static String normalizeAudioUrl(String audioUrl) {
        if (audioUrl == null || audioUrl.isBlank()) {
            return "";
        }
        String value = audioUrl.trim();
        if (value.startsWith("//")) {
            return "https:" + value;
        }
        return value;
    }

    public static String normalizePhonetic(String phonetic) {
        return PhoneticNormalizer.normalize(phonetic);
    }

    public static String normalizeImportSource(String importSource) {
        if (importSource == null || importSource.isBlank()) {
            return "unknown";
        }
        return TextRepairUtils.repair(importSource).trim().toLowerCase(Locale.ROOT);
    }
}
