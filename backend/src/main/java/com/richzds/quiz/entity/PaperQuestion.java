package com.richzds.quiz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 固定卷的题目（表 paper_question）。时间字段均为 UTC。 */
@TableName("paper_question")
public class PaperQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long paperId;
    private Long questionId;
    private Integer score;
    private Integer sort;

    public Long getId() {
        return id;
    }

    public PaperQuestion setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getPaperId() {
        return paperId;
    }

    public PaperQuestion setPaperId(Long paperId) {
        this.paperId = paperId;
        return this;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public PaperQuestion setQuestionId(Long questionId) {
        this.questionId = questionId;
        return this;
    }

    public Integer getScore() {
        return score;
    }

    public PaperQuestion setScore(Integer score) {
        this.score = score;
        return this;
    }

    public Integer getSort() {
        return sort;
    }

    public PaperQuestion setSort(Integer sort) {
        this.sort = sort;
        return this;
    }
}
