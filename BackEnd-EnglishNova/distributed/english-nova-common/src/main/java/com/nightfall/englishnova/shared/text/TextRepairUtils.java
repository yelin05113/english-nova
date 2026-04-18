package com.nightfall.englishnova.shared.text;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 文本乱码修复工具，检测并修复 Windows-1252/ISO-8859-1 编码错误导致的 UTF-8 乱码。
 */
public final class TextRepairUtils {

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final String MOJIBAKE_MARKERS = "ÃÅÆÇÐÑØÙÝÞßàáâãäåæçèéêëìíîïðñòóôõöøùúûüýþÿœšŸŽƒ�";

    private TextRepairUtils() {
    }

    /**
     * 检测字符串是否存在乱码，若存在则尝试从 Windows-1252/ISO-8859-1 还原为 UTF-8。
     *
     * @param value 原始字符串
     * @return 修复后的字符串，若无需修复则返回原始值
     */
    public static String repair(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String trimmed = value.trim();
        if (looksLikePhonetic(trimmed) || !mayNeedRepair(trimmed)) {
            return trimmed;
        }

        String best = trimmed;
        int bestScore = score(trimmed);

        for (Charset sourceCharset : new Charset[]{StandardCharsets.ISO_8859_1, WINDOWS_1252}) {
            String repaired = new String(trimmed.getBytes(sourceCharset), StandardCharsets.UTF_8).trim();
            int repairedScore = score(repaired);
            if (repairedScore > bestScore) {
                best = repaired;
                bestScore = repairedScore;
            }
        }

        return best;
    }

    private static boolean mayNeedRepair(String value) {
        return value.indexOf('\uFFFD') >= 0 || containsMojibakeMarker(value);
    }

    private static boolean containsMojibakeMarker(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (MOJIBAKE_MARKERS.indexOf(value.charAt(index)) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikePhonetic(String value) {
        if (value.length() < 3) {
            return false;
        }

        boolean wrapped = (value.startsWith("/") && value.endsWith("/"))
                || (value.startsWith("[") && value.endsWith("]"));
        if (!wrapped) {
            return false;
        }

        int phoneticChars = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isWhitespace(current)
                    || current == '/'
                    || current == '['
                    || current == ']'
                    || current == '\''
                    || current == 'ˈ'
                    || current == 'ˌ'
                    || current == 'ː'
                    || current == '.'
                    || current == '-') {
                continue;
            }

            if ((current >= 0x0250 && current <= 0x02AF)
                    || current == 'ə'
                    || current == 'ɪ'
                    || current == 'ʊ'
                    || current == 'ɛ'
                    || current == 'æ'
                    || current == 'ɑ'
                    || current == 'ʌ'
                    || current == 'ɔ'
                    || current == 'θ'
                    || current == 'ð'
                    || current == 'ŋ'
                    || current == 'ɜ'
                    || current == 'ɒ'
                    || current == 'ʃ'
                    || current == 'ʒ'
                    || current == 'ɡ'
                    || current == 'ɹ'
                    || current == 'ɚ'
                    || current == 'ɝ'
                    || current == 'ɐ'
                    || Character.isLetter(current)) {
                phoneticChars++;
                continue;
            }

            return false;
        }

        return phoneticChars > 0;
    }

    private static int score(String value) {
        int score = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.UnicodeScript.of(current) == Character.UnicodeScript.HAN) {
                score += 8;
            } else if (current < 128 && Character.isLetterOrDigit(current)) {
                score += 2;
            } else if (Character.isWhitespace(current) || ",.;:!?()[]{}<>-_'/\\\"|@#$%^&*+=~`，。；：！？（）【】《》、·".indexOf(current) >= 0) {
                score += 1;
            } else if (MOJIBAKE_MARKERS.indexOf(current) >= 0) {
                score -= 6;
            } else if (Character.isISOControl(current)) {
                score -= 4;
            }
        }
        return score;
    }
}
