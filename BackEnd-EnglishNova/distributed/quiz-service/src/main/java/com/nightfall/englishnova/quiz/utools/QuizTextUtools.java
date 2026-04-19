package com.nightfall.englishnova.quiz.utools;

public final class QuizTextUtools {

    private QuizTextUtools() {
    }

    public static String normalizePhonetic(String phonetic) {
        if (phonetic == null) {
            return "";
        }
        return phonetic.trim();
    }

    public static boolean hasHanCharacter(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.UnicodeScript.of(value.charAt(index)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }
}
