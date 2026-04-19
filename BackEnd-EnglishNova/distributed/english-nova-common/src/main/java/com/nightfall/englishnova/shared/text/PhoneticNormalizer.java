package com.nightfall.englishnova.shared.text;

public final class PhoneticNormalizer {

    private static final String MOJIBAKE_PHONETIC_MARKERS = "ÃÂÐÑÎÉ";

    private PhoneticNormalizer() {
    }

    public static String normalize(String phonetic) {
        if (phonetic == null) {
            return "";
        }
        String rawCandidate = normalizeCandidate(phonetic);
        String repairedCandidate = normalizeCandidate(TextRepairUtils.repair(phonetic));
        String normalized = score(rawCandidate) >= score(repairedCandidate)
                ? rawCandidate
                : repairedCandidate;
        if (normalized.isBlank()) {
            return "";
        }
        return normalized;
    }

    private static String normalizeCandidate(String phonetic) {
        if (phonetic == null) {
            return "";
        }
        String normalized = phonetic.trim();
        if (normalized.isBlank()) {
            return "";
        }
        normalized = normalized
                .replace('\u04D8', '\u018F')
                .replace('\u04D9', '\u0259')
                .replace('\u04E0', '\u0292')
                .replace('\u04E1', '\u0292')
                .replace('\u04E8', '\u019F')
                .replace('\u04E9', '\u0275')
                .replace('\u0454', '\u025B')
                .replace('\u0404', '\u0190');
        normalized = normalized.replaceAll("[<>]", "");
        normalized = normalized.replaceAll("\\s+", " ");
        return normalized.trim();
    }

    public static boolean hasPlaceholder(String phonetic) {
        String normalized = normalize(phonetic);
        return normalized.isBlank()
                || normalized.indexOf('\uFFFD') >= 0
                || normalized.indexOf('?') >= 0;
    }

    private static int score(String phonetic) {
        if (phonetic == null || phonetic.isBlank()) {
            return Integer.MIN_VALUE;
        }
        int score = 0;
        for (int index = 0; index < phonetic.length(); index++) {
            char current = phonetic.charAt(index);
            if (current == '\uFFFD') {
                score -= 12;
            } else if (current == '?') {
                score -= 10;
            } else if (isPhoneticLetter(current)) {
                score += 4;
            } else if (isPhoneticPunctuation(current)) {
                score += 1;
            } else if (MOJIBAKE_PHONETIC_MARKERS.indexOf(current) >= 0) {
                score -= 6;
            } else {
                score -= 2;
            }
        }
        return score;
    }

    private static boolean isPhoneticLetter(char current) {
        return Character.isLetter(current)
                || (current >= 0x00C0 && current <= 0x024F)
                || (current >= 0x0250 && current <= 0x02AF);
    }

    private static boolean isPhoneticPunctuation(char current) {
        return Character.isWhitespace(current)
                || current == '\''
                || current == ':'
                || current == '.'
                || current == '/'
                || current == '['
                || current == ']'
                || current == '('
                || current == ')'
                || current == '-';
    }
}
