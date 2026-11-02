package com.richzds.quiz.service;

import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import com.richzds.quiz.common.BizException;
import com.richzds.quiz.common.PageQuery;
import com.richzds.quiz.common.PageResult;
import com.richzds.quiz.common.TimeUtil;
import com.richzds.quiz.dto.QuestionDtos.FeedbackRequest;
import com.richzds.quiz.dto.QuestionDtos.FeedbackView;
import com.richzds.quiz.dto.QuestionDtos.QuestionDetail;
import com.richzds.quiz.dto.QuestionDtos.SearchItem;
import com.richzds.quiz.entity.Favorite;
import com.richzds.quiz.entity.Question;
import com.richzds.quiz.entity.QuestionFeedback;
import com.richzds.quiz.exam.ExamGuard;
import com.richzds.quiz.mapper.FavoriteMapper;
import com.richzds.quiz.mapper.QuestionFeedbackMapper;
import com.richzds.quiz.mapper.QuestionMapper;

/** 学员端的搜题、题目详情和纠错。只对外提供已发布的题。 */
@Service
public class QuestionService {

    public static final String STATUS_PUBLISHED = "PUBLISHED";

    private final QuestionMapper questionMapper;
    private final FavoriteMapper favoriteMapper;
    private final QuestionFeedbackMapper feedbackMapper;
    private final CategoryService categoryService;
    private final QuestionAssembler assembler;
    private final ExamGuard examGuard;
    private final Clock clock;

    public QuestionService(QuestionMapper questionMapper, FavoriteMapper favoriteMapper,
                           QuestionFeedbackMapper feedbackMapper, CategoryService categoryService,
                           QuestionAssembler assembler, ExamGuard examGuard, Clock clock) {
        this.questionMapper = questionMapper;
        this.favoriteMapper = favoriteMapper;
        this.feedbackMapper = feedbackMapper;
        this.categoryService = categoryService;
        this.assembler = assembler;
        this.examGuard = examGuard;
        this.clock = clock;
    }

    public PageResult<SearchItem> search(String keyword, Long categoryId, PageQuery pageQuery) {
        CategoryTree tree = categoryService.tree();
        Page<Question> page = pageQuery.toPage();
        Set<Long> chapters = null;
        if (categoryId != null) {
            chapters = tree.chapterIdsUnder(categoryId);
            if (chapters.isEmpty()) {
                return new PageResult<>(List.of(), 0, page.getCurrent(), page.getSize());
            }
        }
        String kw = keyword == null ? null : keyword.strip();
        LambdaQueryWrapper<Question> query = new LambdaQueryWrapper<Question>()
                .select(Question::getId, Question::getCategoryId, Question::getType, Question::getStem)
                .eq(Question::getStatus, STATUS_PUBLISHED)
                .in(chapters != null, Question::getCategoryId, chapters)
                .apply(kw != null && !kw.isEmpty(), "stem LIKE {0}", "%" + escapeLike(kw) + "%")
                .orderByAsc(Question::getCategoryId).orderByAsc(Question::getSort).orderByAsc(Question::getId);
        questionMapper.selectPage(page, query);
        return PageResult.of(page, q -> new SearchItem(q.getId(), q.getCategoryId(), tree.path(q.getCategoryId()),
                q.getType(), assembler.preview(q.getStem())));
    }

    public QuestionDetail detail(long userId, long questionId) {
        Question q = requirePublished(questionId);
        examGuard.assertNotInProgressExam(userId, questionId);
        Favorite fav = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId).eq(Favorite::getQuestionId, questionId));
        return new QuestionDetail(assembler.view(q, categoryService.tree(), fav), assembler.answer(q));
    }

    public FeedbackView feedback(long userId, long questionId, FeedbackRequest req) {
        requirePublished(questionId);
        QuestionFeedback f = new QuestionFeedback()
                .setUserId(userId)
                .setQuestionId(questionId)
                .setType(req.type())
                .setContent(req.content().strip())
                .setStatus("PENDING")
                .setCreatedAt(TimeUtil.nowUtc(clock));
        feedbackMapper.insert(f);
        return new FeedbackView(f.getId(), questionId, f.getType(), f.getContent(), f.getStatus(), null,
                TimeUtil.toInstant(f.getCreatedAt()), null);
    }

    public Question requirePublished(long questionId) {
        Question q = questionMapper.selectById(questionId);
        if (q == null || !STATUS_PUBLISHED.equals(q.getStatus())) {
            throw BizException.notFound("题目");
        }
        return q;
    }

    /** 按 id 批量取题（不限状态，练习和考试里已固定的题即使后来下架也照常显示）。 */
    public Map<Long, Question> loadByIds(Collection<Long> ids) {
        Map<Long, Question> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (Question q : questionMapper.selectBatchIds(ids)) {
            map.put(q.getId(), q);
        }
        return map;
    }

    /** 本人对这些题的收藏，按题 id。 */
    public Map<Long, Favorite> favoritesOf(long userId, Collection<Long> questionIds) {
        Map<Long, Favorite> map = new HashMap<>();
        if (questionIds.isEmpty()) {
            return map;
        }
        for (Favorite f : favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId).in(Favorite::getQuestionId, questionIds))) {
            map.put(f.getQuestionId(), f);
        }
        return map;
    }

    /** 转义 LIKE 里的通配符（两边默认转义符都是反斜杠）。 */
    public static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
