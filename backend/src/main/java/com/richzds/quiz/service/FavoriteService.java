package com.richzds.quiz.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.richzds.quiz.common.PageQuery;
import com.richzds.quiz.common.PageResult;
import com.richzds.quiz.common.TimeUtil;
import com.richzds.quiz.dto.WrongFavoriteDtos.FavoriteItem;
import com.richzds.quiz.entity.Favorite;
import com.richzds.quiz.entity.Question;
import com.richzds.quiz.mapper.FavoriteMapper;

/** 收藏和笔记。 */
@Service
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final QuestionService questionService;
    private final CategoryService categoryService;
    private final QuestionAssembler assembler;
    private final WrongBookService wrongBookService;
    private final UserService userService;
    private final Clock clock;

    public FavoriteService(FavoriteMapper favoriteMapper, QuestionService questionService,
                           CategoryService categoryService, QuestionAssembler assembler,
                           WrongBookService wrongBookService, UserService userService, Clock clock) {
        this.favoriteMapper = favoriteMapper;
        this.questionService = questionService;
        this.categoryService = categoryService;
        this.assembler = assembler;
        this.wrongBookService = wrongBookService;
        this.userService = userService;
        this.clock = clock;
    }

    /** 收藏列表；不传 categoryId 时只看目标考试下的题（没有目标考试时全部）。 */
    public PageResult<FavoriteItem> list(long userId, Long categoryId, PageQuery pageQuery) {
        CategoryTree tree = categoryService.tree();
        Set<Long> chapters = wrongBookService.scopeChapters(userId, categoryId, tree);
        Page<Favorite> page = pageQuery.toPage();
        if (chapters != null && chapters.isEmpty()) {
            return new PageResult<>(List.of(), 0, page.getCurrent(), page.getSize());
        }
        favoriteMapper.selectUserPage(page, userId, chapters);
        Map<Long, Question> questions = questionService.loadByIds(
                page.getRecords().stream().map(Favorite::getQuestionId).toList());
        return PageResult.of(page, page.getRecords().stream()
                .map(f -> item(f, questions.get(f.getQuestionId()), tree)).toList());
    }

    /**
     * 收藏或修改笔记。note 为 null 表示不改笔记（新收藏时为空）；传空串清空笔记。
     */
    @Transactional
    public FavoriteItem upsert(long userId, long questionId, String note) {
        // 同一个人的收藏操作排队：两个请求同时第一次收藏同一道题时，后一个等前一个提交后再读，能看到那一条
        userService.lockRow(userId);
        Question q = questionService.requirePublished(questionId);
        LocalDateTime now = TimeUtil.nowUtc(clock);
        String cleaned = note == null ? null : (note.isBlank() ? "" : note.strip());
        Favorite existing = find(userId, questionId);
        if (existing == null) {
            try {
                existing = new Favorite().setUserId(userId).setQuestionId(questionId)
                        .setNote(cleaned == null || cleaned.isEmpty() ? null : cleaned)
                        .setCreatedAt(now).setUpdatedAt(now);
                favoriteMapper.insert(existing);
                return item(existing, q, categoryService.tree());
            } catch (DuplicateKeyException e) {
                existing = find(userId, questionId);
            }
        }
        if (cleaned != null) {
            favoriteMapper.update(null, new LambdaUpdateWrapper<Favorite>()
                    .eq(Favorite::getId, existing.getId())
                    .set(Favorite::getNote, cleaned.isEmpty() ? null : cleaned)
                    .set(Favorite::getUpdatedAt, now));
            existing.setNote(cleaned.isEmpty() ? null : cleaned);
        }
        return item(existing, q, categoryService.tree());
    }

    /** 取消收藏，笔记一起删除；本来没收藏也返回成功。 */
    @Transactional
    public void remove(long userId, long questionId) {
        favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId).eq(Favorite::getQuestionId, questionId));
    }

    private Favorite find(long userId, long questionId) {
        return favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId).eq(Favorite::getQuestionId, questionId));
    }

    private FavoriteItem item(Favorite f, Question q, CategoryTree tree) {
        return new FavoriteItem(f.getQuestionId(), tree.path(q.getCategoryId()), q.getType(),
                assembler.preview(q.getStem()), f.getNote(), TimeUtil.toInstant(f.getCreatedAt()));
    }
}
