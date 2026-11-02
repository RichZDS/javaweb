package com.richzds.quiz.admin;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.richzds.quiz.common.BizException;
import com.richzds.quiz.common.TimeUtil;
import com.richzds.quiz.dto.AdminDtos.AdminCategory;
import com.richzds.quiz.dto.AdminDtos.CategoryCreate;
import com.richzds.quiz.dto.AdminDtos.CategoryUpdate;
import com.richzds.quiz.entity.Category;
import com.richzds.quiz.entity.ExamPaper;
import com.richzds.quiz.entity.PaperRule;
import com.richzds.quiz.entity.Question;
import com.richzds.quiz.entity.User;
import com.richzds.quiz.mapper.AggRow;
import com.richzds.quiz.mapper.CategoryMapper;
import com.richzds.quiz.mapper.ExamPaperMapper;
import com.richzds.quiz.mapper.PaperRuleMapper;
import com.richzds.quiz.mapper.QuestionMapper;
import com.richzds.quiz.mapper.UserMapper;
import com.richzds.quiz.service.CategoryService;
import com.richzds.quiz.service.CategoryTree;

/** 后台分类管理：三级树，题量含草稿和已发布（不含归档）。 */
@Service
public class AdminCategoryService {

    private final CategoryMapper categoryMapper;
    private final QuestionMapper questionMapper;
    private final ExamPaperMapper paperMapper;
    private final PaperRuleMapper paperRuleMapper;
    private final UserMapper userMapper;
    private final CategoryService categoryService;
    private final Clock clock;

    public AdminCategoryService(CategoryMapper categoryMapper, QuestionMapper questionMapper,
                                ExamPaperMapper paperMapper, PaperRuleMapper paperRuleMapper, UserMapper userMapper,
                                CategoryService categoryService, Clock clock) {
        this.categoryMapper = categoryMapper;
        this.questionMapper = questionMapper;
        this.paperMapper = paperMapper;
        this.paperRuleMapper = paperRuleMapper;
        this.userMapper = userMapper;
        this.categoryService = categoryService;
        this.clock = clock;
    }

    public List<AdminCategory> tree() {
        CategoryTree tree = categoryService.tree();
        Map<Long, Long> counts = activeCounts();
        return tree.roots().stream().map(r -> node(r, tree, counts, true)).toList();
    }

    @Transactional
    public AdminCategory create(CategoryCreate req) {
        int level = 1;
        if (req.parentId() != null) {
            Category parent = categoryMapper.selectById(req.parentId());
            if (parent == null) {
                throw BizException.invalid("parentId", "上级分类不存在");
            }
            if (parent.getLevel() >= CategoryTree.LEVEL_CHAPTER) {
                throw BizException.invalid("parentId", "分类最多 3 级，章节下不能再建分类");
            }
            level = parent.getLevel() + 1;
        }
        Category c = new Category()
                .setParentId(req.parentId())
                .setName(req.name().strip())
                .setLevel(level)
                .setSort(req.sort() == null ? 0 : req.sort())
                .setCreatedAt(TimeUtil.nowUtc(clock));
        categoryMapper.insert(c);
        return new AdminCategory(c.getId(), c.getParentId(), c.getName(), c.getLevel(), c.getSort(), 0, List.of());
    }

    @Transactional
    public AdminCategory update(long id, CategoryUpdate req) {
        Category c = require(id);
        c.setName(req.name().strip());
        if (req.sort() != null) {
            c.setSort(req.sort());
        }
        categoryMapper.updateById(c);
        CategoryTree tree = categoryService.tree();
        return node(tree.get(id), tree, activeCounts(), false);
    }

    /** 有子分类、有题（含已归档）或被试卷用到时 409；删掉的考试若是谁的目标考试，清空其目标。 */
    @Transactional
    public void delete(long id) {
        Category c = require(id);
        if (categoryMapper.selectCount(new LambdaQueryWrapper<Category>().eq(Category::getParentId, id)) > 0) {
            throw BizException.conflict("还有子分类，不能删除");
        }
        long questions = questionMapper.selectCount(new LambdaQueryWrapper<Question>().eq(Question::getCategoryId, id));
        if (questions > 0) {
            throw BizException.conflict("分类下还有 " + questions + " 道题（含已归档），不能删除");
        }
        if (paperMapper.selectCount(new LambdaQueryWrapper<ExamPaper>().eq(ExamPaper::getCategoryId, id)) > 0
                || paperRuleMapper.selectCount(new LambdaQueryWrapper<PaperRule>().eq(PaperRule::getCategoryId, id)) > 0) {
            throw BizException.conflict("有试卷用到这个分类，不能删除");
        }
        if (c.getLevel() == CategoryTree.LEVEL_EXAM) {
            userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getTargetCategoryId, id)
                    .set(User::getTargetCategoryId, null));
        }
        categoryMapper.deleteById(id);
    }

    private Category require(long id) {
        Category c = categoryMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("分类");
        }
        return c;
    }

    private Map<Long, Long> activeCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (AggRow row : questionMapper.countActiveByCategory()) {
            counts.put(row.getGroupId(), row.total());
        }
        return counts;
    }

    private AdminCategory node(Category c, CategoryTree tree, Map<Long, Long> counts, boolean withChildren) {
        long total = 0;
        for (Long chapter : tree.chapterIdsUnder(c.getId())) {
            total += counts.getOrDefault(chapter, 0L);
        }
        List<AdminCategory> children = withChildren
                ? tree.childrenOf(c.getId()).stream().map(ch -> node(ch, tree, counts, true)).toList()
                : List.of();
        return new AdminCategory(c.getId(), c.getParentId(), c.getName(), c.getLevel(), c.getSort(), total, children);
    }
}
