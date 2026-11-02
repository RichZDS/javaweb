package com.richzds.quiz.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 一次正式作答（练习提交或考试结算），统计全部来自这里（表 answer_record）。时间字段均为 UTC。 */
@TableName("answer_record")
public class AnswerRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long questionId;
    private String source;
    private Long sourceId;
    private String userAnswer;
    private Boolean answered;
    private Boolean isCorrect;
    private Long categoryIdSnapshot;
    private Long rootCategoryId;
    private Integer timeSpentSec;
    private LocalDateTime gradedAt;

    public Long getId() {
        return id;
    }

    public AnswerRecord setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getUserId() {
        return userId;
    }

    public AnswerRecord setUserId(Long userId) {
        this.userId = userId;
        return this;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public AnswerRecord setQuestionId(Long questionId) {
        this.questionId = questionId;
        return this;
    }

    public String getSource() {
        return source;
    }

    public AnswerRecord setSource(String source) {
        this.source = source;
        return this;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public AnswerRecord setSourceId(Long sourceId) {
        this.sourceId = sourceId;
        return this;
    }

    public String getUserAnswer() {
        return userAnswer;
    }

    public AnswerRecord setUserAnswer(String userAnswer) {
        this.userAnswer = userAnswer;
        return this;
    }

    public Boolean getAnswered() {
        return answered;
    }

    public AnswerRecord setAnswered(Boolean answered) {
        this.answered = answered;
        return this;
    }

    public Boolean getIsCorrect() {
        return isCorrect;
    }

    public AnswerRecord setIsCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
        return this;
    }

    public Long getCategoryIdSnapshot() {
        return categoryIdSnapshot;
    }

    public AnswerRecord setCategoryIdSnapshot(Long categoryIdSnapshot) {
        this.categoryIdSnapshot = categoryIdSnapshot;
        return this;
    }

    public Long getRootCategoryId() {
        return rootCategoryId;
    }

    public AnswerRecord setRootCategoryId(Long rootCategoryId) {
        this.rootCategoryId = rootCategoryId;
        return this;
    }

    public Integer getTimeSpentSec() {
        return timeSpentSec;
    }

    public AnswerRecord setTimeSpentSec(Integer timeSpentSec) {
        this.timeSpentSec = timeSpentSec;
        return this;
    }

    public LocalDateTime getGradedAt() {
        return gradedAt;
    }

    public AnswerRecord setGradedAt(LocalDateTime gradedAt) {
        this.gradedAt = gradedAt;
        return this;
    }
}
