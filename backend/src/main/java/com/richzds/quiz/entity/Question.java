package com.richzds.quiz.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 题目，题干/选项/解析为 Markdown（表 question）。时间字段均为 UTC。 */
@TableName("question")
public class Question {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String type;
    private String stem;
    private String optionsJson;
    private String answerJson;
    private String fillOptions;
    private String analysis;
    private Integer difficulty;
    private String tags;
    private String imageUrl;
    private String sourceNote;
    private String status;
    private Integer version;
    private Integer sort;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public Question setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public Question setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
        return this;
    }

    public String getType() {
        return type;
    }

    public Question setType(String type) {
        this.type = type;
        return this;
    }

    public String getStem() {
        return stem;
    }

    public Question setStem(String stem) {
        this.stem = stem;
        return this;
    }

    public String getOptionsJson() {
        return optionsJson;
    }

    public Question setOptionsJson(String optionsJson) {
        this.optionsJson = optionsJson;
        return this;
    }

    public String getAnswerJson() {
        return answerJson;
    }

    public Question setAnswerJson(String answerJson) {
        this.answerJson = answerJson;
        return this;
    }

    public String getFillOptions() {
        return fillOptions;
    }

    public Question setFillOptions(String fillOptions) {
        this.fillOptions = fillOptions;
        return this;
    }

    public String getAnalysis() {
        return analysis;
    }

    public Question setAnalysis(String analysis) {
        this.analysis = analysis;
        return this;
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public Question setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
        return this;
    }

    public String getTags() {
        return tags;
    }

    public Question setTags(String tags) {
        this.tags = tags;
        return this;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Question setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
        return this;
    }

    public String getSourceNote() {
        return sourceNote;
    }

    public Question setSourceNote(String sourceNote) {
        this.sourceNote = sourceNote;
        return this;
    }

    public String getStatus() {
        return status;
    }

    public Question setStatus(String status) {
        this.status = status;
        return this;
    }

    public Integer getVersion() {
        return version;
    }

    public Question setVersion(Integer version) {
        this.version = version;
        return this;
    }

    public Integer getSort() {
        return sort;
    }

    public Question setSort(Integer sort) {
        this.sort = sort;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Question setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Question setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
}
