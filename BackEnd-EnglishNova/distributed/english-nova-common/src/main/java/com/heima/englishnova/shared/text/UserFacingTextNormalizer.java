package com.heima.englishnova.shared.text;

import com.ibm.icu.text.Transliterator;

import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

public final class UserFacingTextNormalizer {

    private static final ThreadLocal<Transliterator> TRADITIONAL_TO_SIMPLIFIED =
            ThreadLocal.withInitial(() -> Transliterator.getInstance("Traditional-Simplified"));
    private static final Map<String, String> FALLBACK_REPLACEMENTS = Map.ofEntries(
            Map.entry("為", "为"),
            Map.entry("學", "学"),
            Map.entry("個", "个"),
            Map.entry("兒", "儿"),
            Map.entry("關", "关"),
            Map.entry("係", "系"),
            Map.entry("質", "质"),
            Map.entry("準", "准"),
            Map.entry("無", "无"),
            Map.entry("電", "电"),
            Map.entry("機", "机"),
            Map.entry("書", "书"),
            Map.entry("詞", "词"),
            Map.entry("錄", "录"),
            Map.entry("廣", "广"),
            Map.entry("點", "点"),
            Map.entry("將", "将"),
            Map.entry("們", "们"),
            Map.entry("綫", "线"),
            Map.entry("線", "线"),
            Map.entry("體", "体"),
            Map.entry("軸", "轴"),
            Map.entry("龜", "龟")
    );

    private UserFacingTextNormalizer() {
    }

    public static String normalizeDisplayText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String repaired = TextRepairUtils.repair(value);
        if (repaired == null || repaired.isBlank()) {
            return "";
        }

        String simplified = TRADITIONAL_TO_SIMPLIFIED.get().transliterate(repaired.trim());
        for (Map.Entry<String, String> entry : FALLBACK_REPLACEMENTS.entrySet()) {
            simplified = simplified.replace(entry.getKey(), entry.getValue());
        }
        return simplified
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static String normalizeMeaningText(String value) {
        String normalized = normalizeDisplayText(value);
        if (normalized.isBlank()) {
            return "";
        }

        String[] segments = normalized.split("\\s*(?:/|\\||,|;|、|，|；)\\s*");
        if (segments.length <= 1) {
            return normalized;
        }

        Set<String> deduplicated = new LinkedHashSet<>();
        for (String segment : segments) {
            String candidate = normalizeDisplayText(segment);
            if (!candidate.isBlank()) {
                deduplicated.add(candidate);
            }
        }
        return deduplicated.isEmpty() ? normalized : String.join(" / ", deduplicated);
    }
}
