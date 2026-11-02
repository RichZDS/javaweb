package com.richzds.quiz.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 考试中每题的最新答案（带版本）（表 exam_answer）。时间字段均为 UTC。 */
@TableName("exam_answer")
public class ExamAnswer {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recordId;
    private Long questionId;
    private String userAnswer;
    private Integer version;
    private Boolean marked;
    private LocalDateTime receivedAt;
    private Boolean isCorrect;
    private Integer score;

    public Long getId() {
        return id;
    }

    public ExamAnswer setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getRecordId() {
        return recordId;
    }

    public ExamAnswer setRecordId(Long recordId) {
        this.recordId = recordId;
        return this;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public ExamAnswer setQuestionId(Long questionId) {
        this.questionId = questionId;
        return this;
    }

    public String getUserAnswer() {
        return userAnswer;
    }

    public ExamAnswer setUserAnswer(String userAnswer) {
        this.userAnswer = userAnswer;
        return this;
    }

    public Integer getVersion() {
        return version;
    }

    public ExamAnswer setVersion(Integer version) {
        this.version = version;
        return this;
    }

    public Boolean getMarked() {
        return marked;
    }

    public ExamAnswer setMarked(Boolean marked) {
        this.marked = marked;
        return this;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public ExamAnswer setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
        return this;
    }

    public Boolean getIsCorrect() {
        return isCorrect;
    }

    public ExamAnswer setIsCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
        return this;
    }

    public Integer getScore() {
        return score;
    }

    public ExamAnswer setScore(Integer score) {
        this.score = score;
        return this;
    }
}
