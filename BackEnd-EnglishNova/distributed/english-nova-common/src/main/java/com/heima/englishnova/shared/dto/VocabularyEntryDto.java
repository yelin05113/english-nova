package com.heima.englishnova.shared.dto;

/**
 * 词汇条目信息。
 *
 * @param id              条目 ID
 * @param word            单词
 * @param phonetic        音标
 * @param meaningCn       中文释义
 * @param exampleSentence 例句
 * @param category        分类
 * @param difficulty      难度等级
 * @param visibility      可见性
 */
public record VocabularyEntryDto(
        long id,
        String word,
        String phonetic,
        String meaningCn,
        String exampleSentence,
        String category,
        int difficulty,
        String visibility
) {
}
