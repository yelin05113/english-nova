package com.nightfall.englishnova.search.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * Elasticsearch 搜索词条文档实体。
 */
@Document(indexName = "english-nova-words")
public class SearchEntryDocument {

    /** 文档 ID（与 Elasticsearch _id 一致）。 */
    @Id
    private String id;

    /** 词汇条目 ID。 */
    @Field(type = FieldType.Long)
    private Long entryId;

    /** 条目所属用户 ID。 */
    @Field(type = FieldType.Long)
    private Long ownerUserId;

    /** 条目可见性（PUBLIC / PRIVATE）。 */
    @Field(type = FieldType.Keyword)
    private String visibility;

    /** 所属词书 ID。 */
    @Field(type = FieldType.Long)
    private Long wordbookId;

    /** 单词文本。 */
    @Field(type = FieldType.Text)
    private String word;

    /** 音标。 */
    @Field(type = FieldType.Text)
    private String phonetic;

    /** 中文释义。 */
    @Field(type = FieldType.Text)
    private String meaningCn;

    /** 例句。 */
    @Field(type = FieldType.Text)
    private String exampleSentence;

    /** 分类/词性。 */
    @Field(type = FieldType.Text)
    private String category;

    public SearchEntryDocument() {
    }

    public SearchEntryDocument(
            String id,
            Long entryId,
            Long ownerUserId,
            String visibility,
            Long wordbookId,
            String word,
            String phonetic,
            String meaningCn,
            String exampleSentence,
            String category
    ) {
        this.id = id;
        this.entryId = entryId;
        this.ownerUserId = ownerUserId;
        this.visibility = visibility;
        this.wordbookId = wordbookId;
        this.word = word;
        this.phonetic = phonetic;
        this.meaningCn = meaningCn;
        this.exampleSentence = exampleSentence;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
