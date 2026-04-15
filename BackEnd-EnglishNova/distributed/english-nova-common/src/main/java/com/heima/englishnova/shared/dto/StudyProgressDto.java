package com.heima.englishnova.shared.dto;

/**
 * 学习进度统计。
 *
 * @param totalWords      总单词数
 * @param clearedWords    已掌握数
 * @param inProgressWords 进行中数
 * @param newWords        新词数
 * @param wordbooks       词书数
 * @param answeredQuestions 已答题数
 * @param correctAnswers  正确题数
 * @param accuracyRate    正确率百分比
 */
public record StudyProgressDto(
        int totalWords,
        int clearedWords,
        int inProgressWords,
        int newWords,
        int wordbooks,
        int answeredQuestions,
        int correctAnswers,
        int accuracyRate
) {
}
