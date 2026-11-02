package com.richzds.quiz.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 试卷：固定卷或规则卷（表 exam_paper）。时间字段均为 UTC。 */
@TableName("exam_paper")
public class ExamPaper {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private Long categoryId;
    private String description;
    private String mode;
    private Integer durationMin;
    private Integer totalScore;
    private Integer passScore;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public ExamPaper setId(Long id) {
        this.id = id;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public ExamPaper setTitle(String title) {
        this.title = title;
        return this;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public ExamPaper setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public ExamPaper setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getMode() {
        return mode;
    }

    public ExamPaper setMode(String mode) {
        this.mode = mode;
        return this;
    }

    public Integer getDurationMin() {
        return durationMin;
    }

    public ExamPaper setDurationMin(Integer durationMin) {
        this.durationMin = durationMin;
        return this;
    }

    public Integer getTotalScore() {
        return totalScore;
    }

    public ExamPaper setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
        return this;
    }

    public Integer getPassScore() {
        return passScore;
    }

    public ExamPaper setPassScore(Integer passScore) {
        this.passScore = passScore;
        return this;
    }

    public String getStatus() {
        return status;
    }

    public ExamPaper setStatus(String status) {
        this.status = status;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public ExamPaper setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public ExamPaper setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
}
