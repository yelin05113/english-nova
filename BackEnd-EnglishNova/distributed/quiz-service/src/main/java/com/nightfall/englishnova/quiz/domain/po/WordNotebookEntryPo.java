package com.nightfall.englishnova.quiz.domain.po;

import java.sql.Timestamp;

public class WordNotebookEntryPo {
    private Long id;
    private Long wordNotebookId;
    private String normalizedWord;
    private String word;
    private String phonetic;
    private String meaningCn;
    private String exampleSentence;
    private String correctedExampleSentence;
    private String chineseSentence;
    private String exampleAudioUrl;
    private String optionA;
    private String optionAWord;
    private String optionAMeaningCn;
    private String optionB;
    private String optionBWord;
    private String optionBMeaningCn;
    private String optionC;
    private String optionCWord;
    private String optionCMeaningCn;
    private String optionD;
    private String optionDWord;
    private String optionDMeaningCn;
    private String correctOption;
    private String sourceEntryType;
    private Long sourceEntryId;
    private Timestamp createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWordNotebookId() {
        return wordNotebookId;
    }

    public void setWordNotebookId(Long wordNotebookId) {
        this.wordNotebookId = wordNotebookId;
    }

    public String getNormalizedWord() {
        return normalizedWord;
    }

    public void setNormalizedWord(String normalizedWord) {
        this.normalizedWord = normalizedWord;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getPhonetic() {
        return phonetic;
    }

    public void setPhonetic(String phonetic) {
        this.phonetic = phonetic;
    }

    public String getMeaningCn() {
        return meaningCn;
    }

    public void setMeaningCn(String meaningCn) {
        this.meaningCn = meaningCn;
    }

    public String getExampleSentence() {
        return exampleSentence;
    }

    public void setExampleSentence(String exampleSentence) {
        this.exampleSentence = exampleSentence;
    }

    public String getCorrectedExampleSentence() {
        return correctedExampleSentence;
    }

    public void setCorrectedExampleSentence(String correctedExampleSentence) {
        this.correctedExampleSentence = correctedExampleSentence;
    }

    public String getChineseSentence() {
        return chineseSentence;
    }

    public void setChineseSentence(String chineseSentence) {
        this.chineseSentence = chineseSentence;
    }

    public String getExampleAudioUrl() {
        return exampleAudioUrl;
    }

    public void setExampleAudioUrl(String exampleAudioUrl) {
        this.exampleAudioUrl = exampleAudioUrl;
    }

    public String getOptionA() {
        return optionA;
    }

    public void setOptionA(String optionA) {
        this.optionA = optionA;
    }

    public String getOptionAWord() {
        return optionAWord;
    }

    public void setOptionAWord(String optionAWord) {
        this.optionAWord = optionAWord;
    }

    public String getOptionAMeaningCn() {
        return optionAMeaningCn;
    }

    public void setOptionAMeaningCn(String optionAMeaningCn) {
        this.optionAMeaningCn = optionAMeaningCn;
    }

    public String getOptionB() {
        return optionB;
    }

    public void setOptionB(String optionB) {
        this.optionB = optionB;
    }

    public String getOptionBWord() {
        return optionBWord;
    }

    public void setOptionBWord(String optionBWord) {
        this.optionBWord = optionBWord;
    }

    public String getOptionBMeaningCn() {
        return optionBMeaningCn;
    }

    public void setOptionBMeaningCn(String optionBMeaningCn) {
        this.optionBMeaningCn = optionBMeaningCn;
    }

    public String getOptionC() {
        return optionC;
    }

    public void setOptionC(String optionC) {
        this.optionC = optionC;
    }

    public String getOptionCWord() {
        return optionCWord;
    }

    public void setOptionCWord(String optionCWord) {
        this.optionCWord = optionCWord;
    }

    public String getOptionCMeaningCn() {
        return optionCMeaningCn;
    }

    public void setOptionCMeaningCn(String optionCMeaningCn) {
        this.optionCMeaningCn = optionCMeaningCn;
    }

    public String getOptionD() {
        return optionD;
    }

    public void setOptionD(String optionD) {
        this.optionD = optionD;
    }

    public String getOptionDWord() {
        return optionDWord;
    }

    public void setOptionDWord(String optionDWord) {
        this.optionDWord = optionDWord;
    }

    public String getOptionDMeaningCn() {
        return optionDMeaningCn;
    }

    public void setOptionDMeaningCn(String optionDMeaningCn) {
        this.optionDMeaningCn = optionDMeaningCn;
    }

    public String getCorrectOption() {
        return correctOption;
    }

    public void setCorrectOption(String correctOption) {
        this.correctOption = correctOption;
    }

    public String getSourceEntryType() {
        return sourceEntryType;
    }

    public void setSourceEntryType(String sourceEntryType) {
        this.sourceEntryType = sourceEntryType;
    }

    public Long getSourceEntryId() {
        return sourceEntryId;
    }

    public void setSourceEntryId(Long sourceEntryId) {
        this.sourceEntryId = sourceEntryId;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
