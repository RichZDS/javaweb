package com.richzds.quiz.admin;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.richzds.quiz.common.BizException;
import com.richzds.quiz.common.JsonUtil;
import com.richzds.quiz.common.PageQuery;
import com.richzds.quiz.common.PageResult;
import com.richzds.quiz.common.Rates;
import com.richzds.quiz.common.TimeUtil;
import com.richzds.quiz.domain.AnswerKey;
import com.richzds.quiz.domain.FillOptions;
import com.richzds.quiz.domain.OptionItem;
import com.richzds.quiz.domain.QuestionRules;
import com.richzds.quiz.domain.QuestionType;
import com.richzds.quiz.dto.AdminDtos.AdminQuestion;
import com.richzds.quiz.dto.AdminDtos.AdminQuestionItem;
import com.richzds.quiz.dto.AdminDtos.DeleteResult;
import com.richzds.quiz.dto.AdminDtos.OptionInput;
import com.richzds.quiz.dto.AdminDtos.QuestionInput;
import com.richzds.quiz.dto.QuestionDtos.AnswerView;
import com.richzds.quiz.dto.QuestionDtos.OptionView;
import com.richzds.quiz.entity.AnswerRecord;
import com.richzds.quiz.entity.Category;
import com.richzds.quiz.entity.ExamRecord;
import com.richzds.quiz.entity.Favorite;
import com.richzds.quiz.entity.PaperQuestion;
import com.richzds.quiz.entity.PracticeSession;
import com.richzds.quiz.entity.Question;
import com.richzds.quiz.entity.QuestionFeedback;
import com.richzds.quiz.mapper.AggRow;
import com.richzds.quiz.exam.ExamSettler;
import com.richzds.quiz.mapper.AnswerRecordMapper;
import com.richzds.quiz.mapper.ExamRecordMapper;
import com.richzds.quiz.mapper.FavoriteMapper;
import com.richzds.quiz.mapper.PaperQuestionMapper;
import com.richzds.quiz.mapper.PracticeSessionMapper;
import com.richzds.quiz.mapper.QuestionFeedbackMapper;
import com.richzds.quiz.mapper.QuestionMapper;
import com.richzds.quiz.service.CategoryService;
import com.richzds.quiz.service.CategoryTree;
import com.richzds.quiz.service.PracticeService;
import com.richzds.quiz.service.QuestionAssembler;
import com.richzds.quiz.service.QuestionService;

/**
 * 后台题目管理（docs/api.md 第 9、9.1 节）。发布状态的题必须有完整答案和解析；草稿可以先不填答案。
 * 删除时没人用过就真删，做过、被试卷引用或有人纠错过就改成归档。
 */
@Service
public class AdminQuestionService {

    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = QuestionService.STATUS_PUBLISHED;
    public static final String ARCHIVED = "ARCHIVED";
    private static final Set<String> STATUSES = Set.of(DRAFT, PUBLISHED, ARCHIVED);

    private final QuestionMapper questionMapper;
    private final AnswerRecordMapper answerMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final FavoriteMapper favoriteMapper;
    private final QuestionFeedbackMapper feedbackMapper;
    private final PracticeSessionMapper sessionMapper;
    private final ExamRecordMapper examRecordMapper;
    private final CategoryService categoryService;
    private final QuestionAssembler assembler;
    private final Clock clock;

    public AdminQuestionService(QuestionMapper questionMapper, AnswerRecordMapper answerMapper,
                                PaperQuestionMapper paperQuestionMapper, FavoriteMapper favoriteMapper,
                                QuestionFeedbackMapper feedbackMapper, PracticeSessionMapper sessionMapper,
                                ExamRecordMapper examRecordMapper, CategoryService categoryService,
                                QuestionAssembler assembler, Clock clock) {
        this.questionMapper = questionMapper;
        this.answerMapper = answerMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.favoriteMapper = favoriteMapper;
        this.feedbackMapper = feedbackMapper;
        this.sessionMapper = sessionMapper;
        this.examRecordMapper = examRecordMapper;
        this.categoryService = categoryService;
        this.assembler = assembler;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ 查询

    public PageResult<AdminQuestionItem> list(Long categoryId, String type, String status, String keyword,
                                              PageQuery pageQuery) {
        CategoryTree tree = categoryService.tree();
        Page<Question> page = pageQuery.toPage();
        Set<Long> chapters = categoryId == null ? null : tree.chapterIdsUnder(categoryId);
        if (chapters != null && chapters.isEmpty()) {
            return new PageResult<>(List.of(), 0, page.getCurrent(), page.getSize());
        }
        if (type != null && !type.isBlank()) {
            try {
                QuestionType.valueOf(type.strip());
            } catch (IllegalArgumentException e) {
                throw BizException.invalid("type", "题型不正确");
            }
        }
        if (status != null && !status.isBlank() && !STATUSES.contains(status.strip())) {
            throw BizException.invalid("status", "只能是 DRAFT、PUBLISHED 或 ARCHIVED");
        }
        String kw = keyword == null ? "" : keyword.strip();
        if (kw.codePointCount(0, kw.length()) > QuestionService.MAX_KEYWORD_CHARS) {
            throw BizException.invalid("keyword", "最多 " + QuestionService.MAX_KEYWORD_CHARS + " 字");
        }
        questionMapper.selectPage(page, new LambdaQueryWrapper<Question>()
                .in(chapters != null, Question::getCategoryId, chapters)
                .eq(type != null && !type.isBlank(), Question::getType, type == null ? null : type.strip())
                .eq(status != null && !status.isBlank(), Question::getStatus, status == null ? null : status.strip())
                .apply(!kw.isEmpty(), "stem LIKE {0}", "%" + QuestionService.escapeLike(kw) + "%")
                .orderByDesc(Question::getId));
        Map<Long, AggRow> stats = answerStats(page.getRecords().stream().map(Question::getId).toList());
        return PageResult.of(page, q -> item(q, tree, stats.get(q.getId())));
    }

    public AdminQuestion detail(long id) {
        Question q = require(id);
        return view(q, categoryService.tree(), answerStats(List.of(id)).get(id));
    }

    // ------------------------------------------------------------------ 写入

    @Transactional
    public AdminQuestion create(QuestionInput in) {
        CategoryTree tree = categoryService.tree();
        LocalDateTime now = TimeUtil.nowUtc(clock);
        Question q = new Question().setVersion(1).setCreatedAt(now);
        apply(q, in, tree);
        if (q.getSort() == null) {
            Integer max = questionMapper.maxSort(q.getCategoryId());
            q.setSort(max == null ? 1 : max + 1);
        }
        q.setUpdatedAt(now);
        questionMapper.insert(q);
        return view(q, tree, null);
    }

    @Transactional
    public AdminQuestion update(long id, QuestionInput in) {
        Question q = require(id);
        CategoryTree tree = categoryService.tree();
        Integer oldSort = q.getSort();
        apply(q, in, tree);
        if (q.getSort() == null) {
            q.setSort(oldSort);
        }
        q.setVersion(q.getVersion() + 1).setUpdatedAt(TimeUtil.nowUtc(clock));
        // 用 update wrapper 写全部列，答案、图片等允许被改成 null
        questionMapper.update(null, new LambdaUpdateWrapper<Question>()
                .eq(Question::getId, id)
                .set(Question::getCategoryId, q.getCategoryId())
                .set(Question::getType, q.getType())
                .set(Question::getStem, q.getStem())
                .set(Question::getOptionsJson, q.getOptionsJson())
                .set(Question::getAnswerJson, q.getAnswerJson())
                .set(Question::getFillOptions, q.getFillOptions())
                .set(Question::getAnalysis, q.getAnalysis())
                .set(Question::getDifficulty, q.getDifficulty())
                .set(Question::getTags, q.getTags())
                .set(Question::getImageUrl, q.getImageUrl())
                .set(Question::getSourceNote, q.getSourceNote())
                .set(Question::getStatus, q.getStatus())
                .set(Question::getSort, q.getSort())
                .set(Question::getVersion, q.getVersion())
                .set(Question::getUpdatedAt, q.getUpdatedAt()));
        return view(q, tree, answerStats(List.of(id)).get(id));
    }

    /** 没人做过、没被试卷引用、没有纠错记录、不在没做完的练习或考试里就真删（连同收藏）；否则改成归档。 */
    @Transactional
    public DeleteResult delete(long id) {
        Question q = require(id);
        boolean used = answerMapper.selectCount(new LambdaQueryWrapper<AnswerRecord>()
                .eq(AnswerRecord::getQuestionId, id)) > 0
                || paperQuestionMapper.selectCount(new LambdaQueryWrapper<PaperQuestion>()
                .eq(PaperQuestion::getQuestionId, id)) > 0
                || feedbackMapper.selectCount(new LambdaQueryWrapper<QuestionFeedback>()
                .eq(QuestionFeedback::getQuestionId, id)) > 0
                || inOpenSession(id);
        if (used) {
            questionMapper.update(null, new LambdaUpdateWrapper<Question>().eq(Question::getId, q.getId())
                    .set(Question::getStatus, ARCHIVED).set(Question::getUpdatedAt, TimeUtil.nowUtc(clock)));
            return new DeleteResult(false, true);
        }
        favoriteMapper.delete(new LambdaQueryWrapper<Favorite>().eq(Favorite::getQuestionId, id));
        questionMapper.deleteById(id);
        return new DeleteResult(true, false);
    }

    /**
     * 有没做完的练习或考试含这道题。练习的总题数按开始时的题目 id 列表算，题被真删后这一题交不上，
     * 练习就永远自动完成不了（每日一练写不上 completedAt，连续天数断掉）；考试交卷时还会按快照
     * 给这道题写作答记录和错题。所以这两种情况也只归档。
     * 按存的 JSON 文本匹配：question_ids 是 [1,2,3]，快照里每道题是 {"index":1,"questionId":2,…}，
     * 两边都带上分隔符，12 不会匹配到 123。考试不看截止时间：过了时间还没结算的场次之后照样会结算。
     */
    private boolean inOpenSession(long id) {
        String n = Long.toString(id);
        return sessionMapper.selectCount(new LambdaQueryWrapper<PracticeSession>()
                .eq(PracticeSession::getStatus, PracticeService.STATUS_IN_PROGRESS)
                .and(w -> w.like(PracticeSession::getQuestionIds, "[" + n + "]")
                        .or().like(PracticeSession::getQuestionIds, "[" + n + ",")
                        .or().like(PracticeSession::getQuestionIds, "," + n + ",")
                        .or().like(PracticeSession::getQuestionIds, "," + n + "]"))) > 0
                || examRecordMapper.selectCount(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getStatus, ExamSettler.STATUS_IN_PROGRESS)
                .and(w -> w.like(ExamRecord::getQuestionSnapshot, "\"questionId\":" + n + ",")
                        .or().like(ExamRecord::getQuestionSnapshot, "\"questionId\":" + n + "}"))) > 0;
    }

    /**
     * 把请求体校验后写进实体。不适用当前题型的答案字段直接忽略
     * （例如填空题传了 correctAnswer），只保存适用的那一份。
     */
    void apply(Question q, QuestionInput in, CategoryTree tree) {
        Category chapter = tree.get(in.categoryId());
        if (chapter == null || chapter.getLevel() != CategoryTree.LEVEL_CHAPTER) {
            throw BizException.invalid("categoryId", "必须是三级分类（章节）");
        }
        QuestionType type = QuestionType.valueOf(in.type());
        String status = in.status() == null ? DRAFT : in.status();
        List<OptionItem> options = new ArrayList<>();
        if (in.options() != null) {
            for (OptionInput o : in.options()) {
                options.add(new OptionItem(o == null ? null : o.key(), o == null ? null : o.text()));
            }
        }
        if (type == QuestionType.JUDGE) {
            options = List.of(); // 判断题固定 T/F，不存选项
        }
        String optionError = options.isEmpty() && !PUBLISHED.equals(status) ? null
                : QuestionRules.checkOptions(type, options);
        if (optionError != null) {
            throw BizException.invalid("options", optionError);
        }
        boolean fill = type == QuestionType.FILL;
        List<String> correct = fill ? null : normalizeKeys(in.correctAnswer());
        List<List<String>> accepted = fill ? cleanBlanks(in.acceptedAnswers()) : null;
        boolean hasAnswer = fill ? accepted != null && !accepted.isEmpty() : correct != null && !correct.isEmpty();
        if (PUBLISHED.equals(status) || hasAnswer) {
            String answerError = QuestionRules.check(type, type == QuestionType.JUDGE ? null : options, correct,
                    accepted);
            if (answerError != null) {
                throw BizException.invalid(fill ? "acceptedAnswers" : "correctAnswer", answerError);
            }
        }
        if (PUBLISHED.equals(status) && (in.analysis() == null || in.analysis().isBlank())) {
            throw BizException.invalid("analysis", "发布的题必须有解析");
        }
        String tags = joinTags(in.tags());
        String imageUrl = blankToNull(in.imageUrl());
        if (imageUrl != null && !(imageUrl.startsWith("/uploads/") || imageUrl.startsWith("https://")
                || imageUrl.startsWith("http://"))) {
            throw BizException.invalid("imageUrl", "只能是 /uploads/ 开头的站内路径或 http(s) 地址");
        }
        q.setCategoryId(chapter.getId())
                .setType(type.name())
                .setStem(in.stem())
                .setOptionsJson(type.isChoice() && !options.isEmpty() ? JsonUtil.write(options) : null)
                .setAnswerJson(hasAnswer ? JsonUtil.write(fill ? accepted : correct) : null)
                .setFillOptions(fill ? JsonUtil.write(in.fillOptions() == null ? FillOptions.DEFAULT : in.fillOptions())
                        : null)
                .setAnalysis(blankToNull(in.analysis()))
                .setDifficulty(in.difficulty() == null ? 3 : in.difficulty())
                .setTags(tags)
                .setImageUrl(imageUrl)
                .setSourceNote(blankToNull(in.source()))
                .setStatus(status)
                .setSort(in.sort());
    }

    private static List<String> normalizeKeys(List<String> keys) {
        if (keys == null) {
            return null;
        }
        return keys.stream().map(k -> k.strip().toUpperCase(Locale.ROOT)).toList();
    }

    private static List<List<String>> cleanBlanks(List<List<String>> blanks) {
        if (blanks == null) {
            return null;
        }
        return blanks.stream().map(b -> b.stream().map(s -> s == null ? null : s.strip()).toList()).toList();
    }

    static String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        String joined = String.join(",", tags.stream().map(String::strip).distinct().toList());
        if (joined.length() > 255) {
            throw BizException.invalid("tags", "标签合计太长");
        }
        return joined;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    // ------------------------------------------------------------------ 组装

    private Question require(long id) {
        Question q = questionMapper.selectById(id);
        if (q == null) {
            throw BizException.notFound("题目");
        }
        return q;
    }

    private Map<Long, AggRow> answerStats(List<Long> ids) {
        Map<Long, AggRow> map = new HashMap<>();
        if (!ids.isEmpty()) {
            for (AggRow row : answerMapper.countByQuestions(ids)) {
                map.put(row.getGroupId(), row);
            }
        }
        return map;
    }

    private AdminQuestion view(Question q, CategoryTree tree, AggRow stats) {
        AnswerView a = assembler.answer(q);
        List<OptionView> options = assembler.optionViews(assembler.optionItems(q));
        long total = stats == null ? 0 : stats.total();
        return new AdminQuestion(q.getId(), q.getCategoryId(), tree.path(q.getCategoryId()), q.getType(), q.getStem(),
                assembler.html(q.getStem()), options, a.correctAnswer(), a.acceptedAnswers(), a.fillOptions(),
                q.getAnalysis(), a.analysisHtml(), q.getDifficulty(), QuestionAssembler.tags(q.getTags()),
                q.getImageUrl(), q.getSourceNote(), q.getSort(), q.getStatus(), AnswerKey.of(q).blankCount(),
                q.getVersion(), total, Rates.rate(stats == null ? 0 : stats.correct(), total),
                TimeUtil.toInstant(q.getCreatedAt()), TimeUtil.toInstant(q.getUpdatedAt()));
    }

    private AdminQuestionItem item(Question q, CategoryTree tree, AggRow stats) {
        AnswerView a = assembler.answer(q);
        long total = stats == null ? 0 : stats.total();
        return new AdminQuestionItem(q.getId(), q.getCategoryId(), tree.path(q.getCategoryId()), q.getType(),
                q.getStem(), assembler.preview(q.getStem()), assembler.optionViews(assembler.optionItems(q)),
                a.correctAnswer(), a.acceptedAnswers(), a.fillOptions(), q.getAnalysis(), q.getDifficulty(),
                QuestionAssembler.tags(q.getTags()), q.getImageUrl(), q.getSourceNote(), q.getSort(), q.getStatus(),
                AnswerKey.of(q).blankCount(), q.getVersion(), total,
                Rates.rate(stats == null ? 0 : stats.correct(), total),
                TimeUtil.toInstant(q.getCreatedAt()), TimeUtil.toInstant(q.getUpdatedAt()));
    }
}
