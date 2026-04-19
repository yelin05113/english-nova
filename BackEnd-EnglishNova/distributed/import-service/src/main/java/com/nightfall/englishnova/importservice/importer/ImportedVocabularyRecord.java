package com.nightfall.englishnova.importservice.importer;

/**
 * 导入词汇标准化记录，表示从外部平台解析出来的单条词汇数据。
 *
 * @param word           单词
 * @param phonetic       音标
 * @param meaning        中文释义
 * @param exampleSentence 例句
 * @param category       分类
 * @param difficulty     难度等级
 */
public record ImportedVocabularyRecord(
        String word,
        String phonetic,
        String meaning,
        String exampleSentence,
        String category,
        int difficulty
) {
}
