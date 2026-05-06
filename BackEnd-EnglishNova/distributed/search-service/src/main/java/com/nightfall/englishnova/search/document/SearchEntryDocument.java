package com.nightfall.englishnova.search.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "english-nova-words")
public class SearchEntryDocument {

    @Id
    private String id;

    @Field(type = FieldType.Long)
    private Long entryId;

    @Field(type = FieldType.Keyword)
    private String entryType;

    @Field(type = FieldType.Long)
    private Long ownerUserId;

    @Field(type = FieldType.Keyword)
    private String visibility;

    @Field(type = FieldType.Long)
    private Long wordbookId;

    @Field(type = FieldType.Text)
    private String word;

    @Field(type = FieldType.Text)
    private String phonetic;

    @Field(type = FieldType.Text)
    private String meaningCn;

    @Field(type = FieldType.Text)
    private String exampleSentence;

    @Field(type = FieldType.Text)
    private String correctedEnglish;

    @Field(type = FieldType.Text)
    private String chineseSentence;

    @Field(type = FieldType.Keyword)
    private String exampleAudioUrl;

    @Field(type = FieldType.Text)
    private String category;

    public SearchEntryDocument() {
    }

    public SearchEntryDocument(
            String id,
            Long entryId,
            String entryType,
            Long ownerUserId,
            String visibility,
            Long wordbookId,
            String word,
            String phonetic,
            String meaningCn,
            String exampleSentence,
            String correctedEnglish,
            String chineseSentence,
            String exampleAudioUrl,
            String category
    ) {
        this.id = id;
        this.entryId = entryId;
        this.entryType = entryType;
        this.ownerUserId = ownerUserId;
        this.visibility = visibility;
        this.wordbookId = wordbookId;
        this.word = word;
        this.phonetic = phonetic;
        this.meaningCn = meaningCn;
        this.exampleSentence = exampleSentence;
        this.correctedEnglish = correctedEnglish;
        this.chineseSentence = chineseSentence;
        this.exampleAudioUrl = exampleAudioUrl;
        this.category = category;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getEntryId() {
        return entryId;
    }

    public void setEntryId(Long entryId) {
        this.entryId = entryId;
    }

    public String getEntryType() {
        return entryType;
    }

    public void setEntryType(String entryType) {
        this.entryType = entryType;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public Long getWordbookId() {
        return wordbookId;
    }

    public void setWordbookId(Long wordbookId) {
        this.wordbookId = wordbookId;
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

    public String getCorrectedEnglish() {
        return correctedEnglish;
    }

    public void setCorrectedEnglish(String correctedEnglish) {
        this.correctedEnglish = correctedEnglish;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
