package com.richzds.quiz.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 错题本条目，每人每题一条（表 wrong_question）。时间字段均为 UTC。 */
@TableName("wrong_question")
public class WrongQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long questionId;
    private Integer wrongCount;
    private Integer correctStreak;
    private String lastWrongAnswer;
    private LocalDateTime lastWrongAt;
    private Boolean removed;
    private LocalDateTime removedAt;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public WrongQuestion setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getUserId() {
        return userId;
    }

    public WrongQuestion setUserId(Long userId) {
        this.userId = userId;
        return this;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public WrongQuestion setQuestionId(Long questionId) {
        this.questionId = questionId;
        return this;
    }

    public Integer getWrongCount() {
        return wrongCount;
    }

    public WrongQuestion setWrongCount(Integer wrongCount) {
        this.wrongCount = wrongCount;
        return this;
    }

    public Integer getCorrectStreak() {
        return correctStreak;
    }

    public WrongQuestion setCorrectStreak(Integer correctStreak) {
        this.correctStreak = correctStreak;
        return this;
    }

    public String getLastWrongAnswer() {
        return lastWrongAnswer;
    }

    public WrongQuestion setLastWrongAnswer(String lastWrongAnswer) {
        this.lastWrongAnswer = lastWrongAnswer;
        return this;
    }

    public LocalDateTime getLastWrongAt() {
        return lastWrongAt;
    }

    public WrongQuestion setLastWrongAt(LocalDateTime lastWrongAt) {
        this.lastWrongAt = lastWrongAt;
        return this;
    }

    public Boolean getRemoved() {
        return removed;
    }

    public WrongQuestion setRemoved(Boolean removed) {
        this.removed = removed;
        return this;
    }

    public LocalDateTime getRemovedAt() {
        return removedAt;
    }

    public WrongQuestion setRemovedAt(LocalDateTime removedAt) {
        this.removedAt = removedAt;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public WrongQuestion setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }
}
