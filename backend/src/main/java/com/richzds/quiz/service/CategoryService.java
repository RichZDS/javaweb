package com.richzds.quiz.service;

import org.springframework.stereotype.Service;

import com.richzds.quiz.mapper.CategoryMapper;

/** 分类树的载入。 */
@Service
public class CategoryService {

    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public CategoryTree tree() {
        return new CategoryTree(categoryMapper.selectList(null));
    }
}
