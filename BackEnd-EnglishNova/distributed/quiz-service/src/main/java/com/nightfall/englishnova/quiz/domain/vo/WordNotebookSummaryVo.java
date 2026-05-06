package com.nightfall.englishnova.quiz.domain.vo;

import java.sql.Timestamp;

public class WordNotebookSummaryVo {
    private long id;
    private String name;
    private int wordCount;
    private boolean containsWord;
    private Timestamp createdAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getWordCount() {
        return wordCount;
    }

    public void setWordCount(int wordCount) {
        this.wordCount = wordCount;
    }

    public boolean isContainsWord() {
        return containsWord;
    }

    public void setContainsWord(boolean containsWord) {
        this.containsWord = containsWord;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
