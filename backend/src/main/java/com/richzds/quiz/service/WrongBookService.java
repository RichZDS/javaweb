package com.richzds.quiz.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.richzds.quiz.common.BizException;
import com.richzds.quiz.common.JsonUtil;
import com.richzds.quiz.common.PageQuery;
import com.richzds.quiz.common.PageResult;
import com.richzds.quiz.common.TimeUtil;
import com.richzds.quiz.dto.WrongFavoriteDtos.WrongGroup;
import com.richzds.quiz.dto.WrongFavoriteDtos.WrongItem;
import com.richzds.quiz.entity.Question;
import com.richzds.quiz.entity.WrongQuestion;
import com.richzds.quiz.mapper.UserMapper;
import com.richzds.quiz.mapper.WrongQuestionMapper;

/**
 * 错题本。规则（docs/api.md 第 4 节末尾）：
 * 答错就收录，同一题只一条并累加错误次数；任何地方答错都把连对清零，已移出的重新放回；
 * 在错题练习（WRONG / RETRY）里答对时连对 +1，达到本人的 wrongRemoveThreshold（默认 3）自动移出。
 */
@Service
public class WrongBookService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final WrongQuestionMapper wrongMapper;
    private final QuestionService questionService;
    private final CategoryService categoryService;
    private final QuestionAssembler assembler;
    private final UserMapper userMapper;
    private final Clock clock;

    public WrongBookService(WrongQuestionMapper wrongMapper, QuestionService questionService,
                            CategoryService categoryService, QuestionAssembler assembler, UserMapper userMapper,
                            Clock clock) {
        this.wrongMapper = wrongMapper;
        this.questionService = questionService;
        this.categoryService = categoryService;
        this.assembler = assembler;
        this.userMapper = userMapper;
        this.clock = clock;
    }

    /**
     * 一次正式作答判分后调用（练习提交或考试结算）。
     *
     * @param countsStreak 这次作答是否来自错题练习（答对才累加连对）
     */
    @Transactional
    public void onGraded(long userId, long questionId, boolean correct, List<String> userAnswer,
                         boolean countsStreak) {
        LocalDateTime now = TimeUtil.nowUtc(clock);
        if (!correct) {
            recordWrong(userId, questionId, JsonUtil.write(userAnswer), now);
            return;
        }
        if (!countsStreak) {
            return;
        }
        LambdaUpdateWrapper<WrongQuestion> active = activeEntry(userId, questionId);
        if (wrongMapper.update(null, active.setSql("correct_streak = correct_streak + 1")) == 0) {
            return;
        }
        // 分两步写，避免依赖 MySQL / H2 对同一条 UPDATE 里 SET 求值顺序的差异
        wrongMapper.update(null, activeEntry(userId, questionId)
                .ge(WrongQuestion::getCorrectStreak, UserService.wrongRemoveThreshold(userMapper.selectById(userId)))
                .set(WrongQuestion::getRemoved, true)
                .set(WrongQuestion::getRemovedAt, now));
    }

    private void recordWrong(long userId, long questionId, String answerJson, LocalDateTime now) {
        LambdaUpdateWrapper<WrongQuestion> bump = new LambdaUpdateWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, userId)
                .eq(WrongQuestion::getQuestionId, questionId)
                .setSql("wrong_count = wrong_count + 1")
                .set(WrongQuestion::getCorrectStreak, 0)
                .set(WrongQuestion::getLastWrongAnswer, answerJson)
                .set(WrongQuestion::getLastWrongAt, now)
                .set(WrongQuestion::getRemoved, false)
                .set(WrongQuestion::getRemovedAt, null);
        if (wrongMapper.update(null, bump) > 0) {
            return;
        }
        try {
            wrongMapper.insert(new WrongQuestion()
                    .setUserId(userId)
                    .setQuestionId(questionId)
                    .setWrongCount(1)
                    .setCorrectStreak(0)
                    .setLastWrongAnswer(answerJson)
                    .setLastWrongAt(now)
                    .setRemoved(false)
                    .setCreatedAt(now));
        } catch (DuplicateKeyException e) {
            // 并发下另一请求刚插入了这一条，退回到累加
            wrongMapper.update(null, bump);
        }
    }

    private static LambdaUpdateWrapper<WrongQuestion> activeEntry(long userId, long questionId) {
        return new LambdaUpdateWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, userId)
                .eq(WrongQuestion::getQuestionId, questionId)
                .eq(WrongQuestion::getRemoved, false);
    }

    /** 错题列表；不传 categoryId 时只看目标考试下的题（没有目标考试时全部）。 */
    public PageResult<WrongItem> list(long userId, Long categoryId, String sort, PageQuery pageQuery) {
        String order = sort == null || sort.isBlank() ? "RECENT" : sort.strip();
        if (!"RECENT".equals(order) && !"MOST".equals(order)) {
            throw BizException.invalid("sort", "只能是 RECENT 或 MOST");
        }
        CategoryTree tree = categoryService.tree();
        Set<Long> chapters = scopeChapters(userId, categoryId, tree);
        Page<WrongQuestion> page = pageQuery.toPage();
        if (chapters != null && chapters.isEmpty()) {
            return new PageResult<>(List.of(), 0, page.getCurrent(), page.getSize());
        }
        wrongMapper.selectActivePage(page, userId, chapters, order);
        Map<Long, Question> questions = questionService.loadByIds(
                page.getRecords().stream().map(WrongQuestion::getQuestionId).toList());
        List<WrongItem> items = page.getRecords().stream().map(w -> {
            Question q = questions.get(w.getQuestionId());
            return new WrongItem(w.getQuestionId(), q.getCategoryId(), tree.path(q.getCategoryId()), q.getType(),
                    assembler.preview(q.getStem()), w.getWrongCount(), w.getCorrectStreak(),
                    JsonUtil.read(w.getLastWrongAnswer(), STRING_LIST), TimeUtil.toInstant(w.getLastWrongAt()));
        }).toList();
        return PageResult.of(page, items);
    }

    /** 按章节分组计数，按分类树顺序排列；范围同列表的默认范围（目标考试，没有时全部）。 */
    public List<WrongGroup> groups(long userId) {
        CategoryTree tree = categoryService.tree();
        Set<Long> chapters = scopeChapters(userId, null, tree);
        return wrongMapper.countActiveByChapter(userId).stream()
                .filter(r -> chapters == null || chapters.contains(r.getGroupId()))
                .sorted(Comparator.comparingInt(r -> tree.order(r.getGroupId())))
                .map(r -> new WrongGroup(r.getGroupId(), tree.path(r.getGroupId()), r.total()))
                .toList();
    }

    /**
     * 错题本、收藏、错题 / 收藏练习共用的范围（docs/api.md 第 5 节）：传了分类就是该分类子树里的章节；
     * 没传时是本人目标考试下的章节；没有目标考试返回 null，表示不限。
     */
    public Set<Long> scopeChapters(long userId, Long categoryId, CategoryTree tree) {
        if (categoryId != null) {
            return tree.chapterIdsUnder(categoryId);
        }
        Long target = userMapper.selectById(userId).getTargetCategoryId();
        return target == null ? null : tree.chapterIdsUnder(target);
    }

    /** 手动移出：只改状态，保留错误次数等历史。 */
    @Transactional
    public void remove(long userId, long questionId) {
        int updated = wrongMapper.update(null, activeEntry(userId, questionId)
                .set(WrongQuestion::getRemoved, true)
                .set(WrongQuestion::getRemovedAt, TimeUtil.nowUtc(clock)));
        if (updated == 0) {
            throw BizException.notFound("错题");
        }
    }

}
