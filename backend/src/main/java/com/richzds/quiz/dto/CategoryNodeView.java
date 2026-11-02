package com.richzds.quiz.dto;

import java.util.List;

/** 学员端分类树节点，带本人进度（docs/api.md 第 3 节）。 */
public record CategoryNodeView(Long id, Long parentId, String name, Integer level, Integer sort,
                               long questionCount, long doneCount, Double correctRate,
                               List<CategoryNodeView> children) {
}
