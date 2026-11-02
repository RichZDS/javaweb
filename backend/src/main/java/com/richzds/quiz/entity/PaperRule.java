package com.richzds.quiz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 规则卷的抽题规则（表 paper_rule）。时间字段均为 UTC。 */
@TableName("paper_rule")
public class PaperRule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long paperId;
    private Long categoryId;
    private String type;
    private Integer count;
    private Integer score;
    private Integer sort;

    public Long getId() {
        return id;
    }

    public PaperRule setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getPaperId() {
        return paperId;
    }

    public PaperRule setPaperId(Long paperId) {
        this.paperId = paperId;
        return this;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public PaperRule setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
        return this;
    }

    public String getType() {
        return type;
    }

    public PaperRule setType(String type) {
        this.type = type;
        return this;
    }

    public Integer getCount() {
        return count;
    }

    public PaperRule setCount(Integer count) {
        this.count = count;
        return this;
    }

    public Integer getScore() {
        return score;
    }

    public PaperRule setScore(Integer score) {
        this.score = score;
        return this;
    }

    public Integer getSort() {
        return sort;
    }

    public PaperRule setSort(Integer sort) {
        this.sort = sort;
        return this;
    }
}
