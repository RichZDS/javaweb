package com.richzds.quiz.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 一次练习，题目列表在开始时固定（表 practice_session）。时间字段均为 UTC。 */
@TableName("practice_session")
public class PracticeSession {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String mode;
    private Boolean reciteMode;
    private String paramsJson;
    private String questionIds;
    private Integer currentIndex;
    private String status;
    private Long rootCategoryId;
    private Long sequentialCategoryId;
    private LocalDate dailyDate;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public PracticeSession setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getUserId() {
        return userId;
    }

    public PracticeSession setUserId(Long userId) {
        this.userId = userId;
        return this;
    }

    public String getMode() {
        return mode;
    }

    public PracticeSession setMode(String mode) {
        this.mode = mode;
        return this;
    }

    public Boolean getReciteMode() {
        return reciteMode;
    }

    public PracticeSession setReciteMode(Boolean reciteMode) {
        this.reciteMode = reciteMode;
        return this;
    }

    public String getParamsJson() {
        return paramsJson;
    }

    public PracticeSession setParamsJson(String paramsJson) {
        this.paramsJson = paramsJson;
        return this;
    }

    public String getQuestionIds() {
        return questionIds;
    }

    public PracticeSession setQuestionIds(String questionIds) {
        this.questionIds = questionIds;
        return this;
    }

    public Integer getCurrentIndex() {
        return currentIndex;
    }

    public PracticeSession setCurrentIndex(Integer currentIndex) {
        this.currentIndex = currentIndex;
        return this;
    }

    public String getStatus() {
        return status;
    }

    public PracticeSession setStatus(String status) {
        this.status = status;
        return this;
    }

    public Long getRootCategoryId() {
        return rootCategoryId;
    }

    public PracticeSession setRootCategoryId(Long rootCategoryId) {
        this.rootCategoryId = rootCategoryId;
        return this;
    }

    public Long getSequentialCategoryId() {
        return sequentialCategoryId;
    }

    public PracticeSession setSequentialCategoryId(Long sequentialCategoryId) {
        this.sequentialCategoryId = sequentialCategoryId;
        return this;
    }

    public LocalDate getDailyDate() {
        return dailyDate;
    }

    public PracticeSession setDailyDate(LocalDate dailyDate) {
        this.dailyDate = dailyDate;
        return this;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public PracticeSession setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
        return this;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public PracticeSession setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public PracticeSession setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
}
