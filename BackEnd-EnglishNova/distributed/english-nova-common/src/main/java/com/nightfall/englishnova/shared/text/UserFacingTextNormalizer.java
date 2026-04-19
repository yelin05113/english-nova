package com.nightfall.englishnova.shared.text;

import com.ibm.icu.text.Transliterator;

import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 面向用户的文本规范化工具，提供繁体转简体、HTML 清理与多义词去重等能力。
 */
public final class UserFacingTextNormalizer {

    private static final Pattern ANGLE_SEGMENT = Pattern.compile("<\\s*([^<>]{0,24})\\s*>");

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

    /**
     * 规范化面向用户展示的文本内容，执行繁简转换与空白压缩。
     *
     * @param value 原始文本
     * @return 规范化后的文本
     */
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
        String sanitized = sanitizeMarkupArtifacts(simplified);
        return sanitized
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 规范化释义文本，在展示规范化基础上对多义词进行去重合并。
     *
     * @param value 原始释义文本
     * @return 规范化后的释义文本
     */
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

    private static String sanitizeMarkupArtifacts(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String sanitized = value;
        sanitized = sanitized.replaceAll("(?i)<\\s*br\\s*/?\\s*>", " ");
        sanitized = sanitized.replaceAll("(?i)</\\s*(?:p|div|li|ul|ol|span|strong|em|b|i|u|small|font|sup|sub)\\s*>", " ");
        sanitized = sanitized.replaceAll("(?i)<\\s*(?:p|div|li|ul|ol|span|strong|em|b|i|u|small|font|sup|sub)(?:\\s+[^<>]*)?>", " ");
        sanitized = unwrapAngleSegments(sanitized);
        sanitized = sanitized.replaceAll("</?[^<>]+>", " ");
        sanitized = sanitized.replaceAll("\\s+", " ");
        return sanitized.trim();
    }

    private static String unwrapAngleSegments(String value) {
        Matcher matcher = ANGLE_SEGMENT.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String inner = matcher.group(1) == null ? "" : matcher.group(1).trim();
            String replacement;
            if (inner.isBlank() || inner.matches("[?\\-_/!*=+.:;]+")) {
                replacement = " ";
            } else if (inner.matches("(?i)/?(?:p|div|li|ul|ol|span|strong|em|b|i|u|small|font|sup|sub|br)\\b.*")) {
                replacement = " ";
            } else {
                replacement = inner;
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
