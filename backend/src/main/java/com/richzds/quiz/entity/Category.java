package com.richzds.quiz.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 分类：1 考试 / 2 科目 / 3 章节（表 category）。时间字段均为 UTC。 */
@TableName("category")
public class Category {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String name;
    private Integer level;
    private Integer sort;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public Category setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getParentId() {
        return parentId;
    }

    public Category setParentId(Long parentId) {
        this.parentId = parentId;
        return this;
    }

    public String getName() {
        return name;
    }

    public Category setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getLevel() {
        return level;
    }

    public Category setLevel(Integer level) {
        this.level = level;
        return this;
    }

    public Integer getSort() {
        return sort;
    }

    public Category setSort(Integer sort) {
        this.sort = sort;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Category setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }
}
