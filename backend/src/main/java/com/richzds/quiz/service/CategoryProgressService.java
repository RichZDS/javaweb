package com.richzds.quiz.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.richzds.quiz.common.BizException;
import com.richzds.quiz.common.Rates;
import com.richzds.quiz.dto.CategoryNodeView;
import com.richzds.quiz.entity.Category;
import com.richzds.quiz.mapper.AggRow;
import com.richzds.quiz.mapper.AnswerRecordMapper;
import com.richzds.quiz.mapper.QuestionMapper;

/** 学员端分类树：题量、本人做过的不同题数和正确率，从章节逐级汇总到上层。 */
@Service
public class CategoryProgressService {

    private final CategoryService categoryService;
    private final QuestionMapper questionMapper;
    private final AnswerRecordMapper answerRecordMapper;

    public CategoryProgressService(CategoryService categoryService, QuestionMapper questionMapper,
                                   AnswerRecordMapper answerRecordMapper) {
        this.categoryService = categoryService;
        this.questionMapper = questionMapper;
        this.answerRecordMapper = answerRecordMapper;
    }

    /** 全部一级分类（children 为空数组），用于选目标考试。 */
    public List<CategoryNodeView> roots(long userId) {
        CategoryTree tree = categoryService.tree();
        Builder builder = builder(tree, userId);
        return tree.roots().stream().map(r -> builder.build(r, false)).toList();
    }

    /** 某个考试（一级分类）的整棵树，返回根节点。 */
    public CategoryNodeView tree(long userId, long rootId) {
        CategoryTree tree = categoryService.tree();
        Category root = tree.get(rootId);
        if (root == null || root.getLevel() != CategoryTree.LEVEL_EXAM) {
            throw BizException.notFound("考试分类");
        }
        return builder(tree, userId).build(root, true);
    }

    private Builder builder(CategoryTree tree, long userId) {
        Map<Long, Long> questionCounts = new HashMap<>();
        for (AggRow row : questionMapper.countPublishedByCategory()) {
            questionCounts.put(row.getGroupId(), row.total());
        }
        Map<Long, AggRow> progress = new HashMap<>();
        for (AggRow row : answerRecordMapper.progressByChapter(userId)) {
            progress.put(row.getGroupId(), row);
        }
        return new Builder(tree, questionCounts, progress);
    }

    private record Builder(CategoryTree tree, Map<Long, Long> questionCounts, Map<Long, AggRow> progress) {

        CategoryNodeView build(Category node, boolean withChildren) {
            long questions = 0;
            long done = 0;
            long answers = 0;
            long correct = 0;
            for (Long chapterId : tree.chapterIdsUnder(node.getId())) {
                questions += questionCounts.getOrDefault(chapterId, 0L);
                AggRow p = progress.get(chapterId);
                if (p != null) {
                    done += p.distinct();
                    answers += p.total();
                    correct += p.correct();
                }
            }
            List<CategoryNodeView> children = withChildren
                    ? tree.childrenOf(node.getId()).stream().map(c -> build(c, true)).toList()
                    : List.of();
            return new CategoryNodeView(node.getId(), node.getParentId(), node.getName(), node.getLevel(),
                    node.getSort(), questions, done, Rates.rate(correct, answers), children);
        }
    }
}
