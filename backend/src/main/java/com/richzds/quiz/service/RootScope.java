package com.richzds.quiz.service;

import org.springframework.stereotype.Component;

import com.richzds.quiz.common.BizException;
import com.richzds.quiz.entity.Category;

/**
 * “按考试看”的范围，统计、继续练习、练习记录、考试记录共用（docs/api.md 0.4 节、第 7 节）：
 * 传了 categoryId 就是这个一级分类（不是一级分类返回 400，fields 指向 categoryId），否则本人目标考试；
 * 都没有时返回 null，表示不限。
 */
@Component
public class RootScope {

    private final CategoryService categoryService;
    private final UserService userService;

    public RootScope(CategoryService categoryService, UserService userService) {
        this.categoryService = categoryService;
        this.userService = userService;
    }

    public Long resolve(long userId, Long categoryId) {
        if (categoryId != null) {
            Category c = categoryService.tree().get(categoryId);
            if (c == null || c.getLevel() != CategoryTree.LEVEL_EXAM) {
                throw BizException.invalid("categoryId", "必须是一级分类（考试）");
            }
            return categoryId;
        }
        return userService.requireUser(userId).getTargetCategoryId();
    }

    /** 一级分类的名称；id 为空或分类已删除时为 null。 */
    public String nameOf(CategoryTree tree, Long rootId) {
        Category c = rootId == null ? null : tree.get(rootId);
        return c == null ? null : c.getName();
    }
}
