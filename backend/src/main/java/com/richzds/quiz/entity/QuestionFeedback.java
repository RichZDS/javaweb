package com.richzds.quiz.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 题目纠错反馈（表 question_feedback）。时间字段均为 UTC。 */
@TableName("question_feedback")
public class QuestionFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long questionId;
    private String type;
    private String content;
    private String status;
    private String reply;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;

    public Long getId() {
        return id;
    }

    public QuestionFeedback setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getUserId() {
        return userId;
    }

    public QuestionFeedback setUserId(Long userId) {
        this.userId = userId;
        return this;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public QuestionFeedback setQuestionId(Long questionId) {
        this.questionId = questionId;
        return this;
    }

    public String getType() {
        return type;
    }

    public QuestionFeedback setType(String type) {
        this.type = type;
        return this;
    }

    public String getContent() {
        return content;
    }

    public QuestionFeedback setContent(String content) {
        this.content = content;
        return this;
    }

    public String getStatus() {
        return status;
    }

    public QuestionFeedback setStatus(String status) {
        this.status = status;
        return this;
    }

    public String getReply() {
        return reply;
    }

    public QuestionFeedback setReply(String reply) {
        this.reply = reply;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public QuestionFeedback setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public LocalDateTime getHandledAt() {
        return handledAt;
    }

    public QuestionFeedback setHandledAt(LocalDateTime handledAt) {
        this.handledAt = handledAt;
        return this;
    }
}
