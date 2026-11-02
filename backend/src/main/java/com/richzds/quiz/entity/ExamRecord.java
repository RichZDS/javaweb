package com.richzds.quiz.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 一场考试，开考时存题目快照（表 exam_record）。时间字段均为 UTC。 */
@TableName("exam_record")
public class ExamRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long paperId;
    private String questionSnapshot;
    private Integer totalScore;
    private Integer passScore;
    private LocalDateTime startedAt;
    private LocalDateTime deadlineAt;
    private LocalDateTime submittedAt;
    private Integer score;
    private Boolean passed;
    private String status;
    private String finishReason;

    public Long getId() {
        return id;
    }

    public ExamRecord setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getUserId() {
        return userId;
    }

    public ExamRecord setUserId(Long userId) {
        this.userId = userId;
        return this;
    }

    public Long getPaperId() {
        return paperId;
    }

    public ExamRecord setPaperId(Long paperId) {
        this.paperId = paperId;
        return this;
    }

    public String getQuestionSnapshot() {
        return questionSnapshot;
    }

    public ExamRecord setQuestionSnapshot(String questionSnapshot) {
        this.questionSnapshot = questionSnapshot;
        return this;
    }

    public Integer getTotalScore() {
        return totalScore;
    }

    public ExamRecord setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
        return this;
    }

    public Integer getPassScore() {
        return passScore;
    }

    public ExamRecord setPassScore(Integer passScore) {
        this.passScore = passScore;
        return this;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public ExamRecord setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
        return this;
    }

    public LocalDateTime getDeadlineAt() {
        return deadlineAt;
    }

    public ExamRecord setDeadlineAt(LocalDateTime deadlineAt) {
        this.deadlineAt = deadlineAt;
        return this;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public ExamRecord setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
        return this;
    }

    public Integer getScore() {
        return score;
    }

    public ExamRecord setScore(Integer score) {
        this.score = score;
        return this;
    }

    public Boolean getPassed() {
        return passed;
    }

    public ExamRecord setPassed(Boolean passed) {
        this.passed = passed;
        return this;
    }

    public String getStatus() {
        return status;
    }

    public ExamRecord setStatus(String status) {
        this.status = status;
        return this;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public ExamRecord setFinishReason(String finishReason) {
        this.finishReason = finishReason;
        return this;
    }
}
