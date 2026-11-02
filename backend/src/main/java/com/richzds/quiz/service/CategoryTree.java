package com.richzds.quiz.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.richzds.quiz.entity.Category;

/**
 * 一次性载入的分类树快照（分类表很小，按请求现查现建）。
 * 提供路径、根节点、子树里的章节、先序位置等查询。
 */
public final class CategoryTree {

    public static final int LEVEL_EXAM = 1;
    public static final int LEVEL_SUBJECT = 2;
    public static final int LEVEL_CHAPTER = 3;

    private static final Comparator<Category> ORDER =
            Comparator.comparing(Category::getSort).thenComparing(Category::getId);

    private final Map<Long, Category> byId = new HashMap<>();
    private final Map<Long, List<Category>> children = new HashMap<>();
    private final List<Category> roots = new ArrayList<>();
    private final Map<Long, Integer> preorder = new HashMap<>();
    private final Map<Long, String> paths = new HashMap<>();

    public CategoryTree(Collection<Category> all) {
        for (Category c : all) {
            byId.put(c.getId(), c);
        }
        for (Category c : all) {
            if (c.getParentId() == null) {
                roots.add(c);
            } else {
                children.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
            }
        }
        roots.sort(ORDER);
        children.values().forEach(list -> list.sort(ORDER));
        int[] counter = {0};
        for (Category root : roots) {
            walk(root, "", counter);
        }
    }

    private void walk(Category node, String parentPath, int[] counter) {
        preorder.put(node.getId(), counter[0]++);
        String path = parentPath.isEmpty() ? node.getName() : parentPath + " / " + node.getName();
        paths.put(node.getId(), path);
        for (Category child : childrenOf(node.getId())) {
            walk(child, path, counter);
        }
    }

    public Category get(Long id) {
        return id == null ? null : byId.get(id);
    }

    public boolean exists(Long id) {
        return get(id) != null;
    }

    public List<Category> roots() {
        return roots;
    }

    public List<Category> childrenOf(Long id) {
        return children.getOrDefault(id, List.of());
    }

    /** 形如 “考研 408 / 数据结构 / 线性表”；未知 id 返回空串。 */
    public String path(Long id) {
        return id == null ? "" : paths.getOrDefault(id, "");
    }

    /** 所属一级分类（考试）的 id。 */
    public Long rootOf(Long id) {
        Category c = get(id);
        while (c != null && c.getParentId() != null) {
            c = get(c.getParentId());
        }
        return c == null ? null : c.getId();
    }

    /** 子树（含自身）里所有三级分类（章节）的 id。 */
    public Set<Long> chapterIdsUnder(Long id) {
        Set<Long> out = new LinkedHashSet<>();
        collectChapters(get(id), out);
        return out;
    }

    public Set<Long> chapterIdsUnder(Collection<Long> ids) {
        Set<Long> out = new LinkedHashSet<>();
        for (Long id : ids) {
            collectChapters(get(id), out);
        }
        return out;
    }

    private void collectChapters(Category c, Set<Long> out) {
        if (c == null) {
            return;
        }
        if (c.getLevel() == LEVEL_CHAPTER) {
            out.add(c.getId());
            return;
        }
        for (Category child : childrenOf(c.getId())) {
            collectChapters(child, out);
        }
    }

    /** 按树的先序位置排序用的序号；未知 id 排最后。 */
    public int order(Long id) {
        return preorder.getOrDefault(id, Integer.MAX_VALUE);
    }
}
