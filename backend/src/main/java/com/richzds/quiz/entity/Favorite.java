package com.richzds.quiz.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 收藏和笔记（表 favorite）。时间字段均为 UTC。 */
@TableName("favorite")
public class Favorite {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long questionId;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public Favorite setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getUserId() {
        return userId;
    }

    public Favorite setUserId(Long userId) {
        this.userId = userId;
        return this;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public Favorite setQuestionId(Long questionId) {
        this.questionId = questionId;
        return this;
    }

    public String getNote() {
        return note;
    }

    public Favorite setNote(String note) {
        this.note = note;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Favorite setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Favorite setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
}
