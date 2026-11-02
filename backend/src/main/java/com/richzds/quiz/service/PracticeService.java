package com.richzds.quiz.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.richzds.quiz.common.BizException;
import com.richzds.quiz.common.ErrorCode;
import com.richzds.quiz.common.JsonUtil;
import com.richzds.quiz.common.PageQuery;
import com.richzds.quiz.common.PageResult;
import com.richzds.quiz.common.Rates;
import com.richzds.quiz.common.TimeUtil;
import com.richzds.quiz.domain.AnswerKey;
import com.richzds.quiz.domain.PracticeMode;
import com.richzds.quiz.domain.QuestionType;
import com.richzds.quiz.dto.PracticeDtos.FinishResult;
import com.richzds.quiz.dto.PracticeDtos.SessionListItem;
import com.richzds.quiz.dto.PracticeDtos.SessionQuestion;
import com.richzds.quiz.dto.PracticeDtos.SessionView;
import com.richzds.quiz.dto.PracticeDtos.StartRequest;
import com.richzds.quiz.dto.PracticeDtos.SubmitRequest;
import com.richzds.quiz.dto.PracticeDtos.SubmitResult;
import com.richzds.quiz.dto.QuestionDtos.AnswerView;
import com.richzds.quiz.entity.AnswerRecord;
import com.richzds.quiz.entity.ExamPaper;
import com.richzds.quiz.entity.ExamRecord;
import com.richzds.quiz.entity.Favorite;
import com.richzds.quiz.entity.PracticeSession;
import com.richzds.quiz.entity.Question;
import com.richzds.quiz.entity.User;
import com.richzds.quiz.exam.ExamSnapshot;
import com.richzds.quiz.mapper.AggRow;
import com.richzds.quiz.mapper.AnswerRecordMapper;
import com.richzds.quiz.mapper.ExamPaperMapper;
import com.richzds.quiz.mapper.ExamRecordMapper;
import com.richzds.quiz.mapper.PracticeSessionMapper;
import com.richzds.quiz.mapper.QuestionMapper;

/**
 * 练习：开始（六种抽题方式 + 重做错题）、取题、提交判分、进度、结束、记录。
 * 规则见 docs/api.md 第 4 节。题目列表在开始时固定；每题只能正式提交一次。
 */
@Service
public class PracticeService {

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String SOURCE_PRACTICE = "PRACTICE";
    public static final String SOURCE_EXAM = "EXAM";

    static final Set<Integer> ALLOWED_COUNTS = Set.of(10, 20, 50);
    static final int MAX_LIST_COUNT = 100;
    static final int DAILY_COUNT = 10;

    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() { };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final PracticeSessionMapper sessionMapper;
    private final AnswerRecordMapper answerMapper;
    private final QuestionMapper questionMapper;
    private final ExamRecordMapper examRecordMapper;
    private final ExamPaperMapper examPaperMapper;
    private final QuestionService questionService;
    private final CategoryService categoryService;
    private final QuestionAssembler assembler;
    private final GradingService gradingService;
    private final WrongBookService wrongBookService;
    private final UserService userService;
    private final Clock clock;

    public PracticeService(PracticeSessionMapper sessionMapper, AnswerRecordMapper answerMapper,
                           QuestionMapper questionMapper, ExamRecordMapper examRecordMapper,
                           ExamPaperMapper examPaperMapper, QuestionService questionService,
                           CategoryService categoryService, QuestionAssembler assembler,
                           GradingService gradingService, WrongBookService wrongBookService,
                           UserService userService, Clock clock) {
        this.sessionMapper = sessionMapper;
        this.answerMapper = answerMapper;
        this.questionMapper = questionMapper;
        this.examRecordMapper = examRecordMapper;
        this.examPaperMapper = examPaperMapper;
        this.questionService = questionService;
        this.categoryService = categoryService;
        this.assembler = assembler;
        this.gradingService = gradingService;
        this.wrongBookService = wrongBookService;
        this.userService = userService;
        this.clock = clock;
    }

    /** 开始的结果：created=false 表示返回的是已有的那一次（HTTP 200），否则新建（HTTP 201）。 */
    public record Started(SessionView session, boolean created) {
    }

    // ------------------------------------------------------------------ 开始

    @Transactional
    public Started start(long userId, StartRequest req) {
        PracticeMode mode = PracticeMode.valueOf(req.mode());
        User user = userService.requireUser(userId);
        CategoryTree tree = categoryService.tree();
        return switch (mode) {
            case DAILY -> startDaily(user, tree, req);
            case SEQUENTIAL -> startSequential(user, tree, req);
            case RETRY -> startRetry(user, req);
            default -> startPooled(user, tree, mode, req);
        };
    }

    /** RANDOM / WRONG / FAVORITE / TAG：按条件取候选池，打乱后取前 N 道。 */
    private Started startPooled(User user, CategoryTree tree, PracticeMode mode, StartRequest req) {
        boolean listMode = mode == PracticeMode.WRONG || mode == PracticeMode.FAVORITE;
        Integer count = req.count();
        if (count != null && !ALLOWED_COUNTS.contains(count)) {
            throw BizException.invalid("count", "题量只能是 10、20 或 50");
        }
        if (count == null && !listMode) {
            throw BizException.invalid("count", "不能为空");
        }
        String tagLike = null;
        if (mode == PracticeMode.TAG) {
            if (req.tag() == null || req.tag().isBlank()) {
                throw BizException.invalid("tag", "不能为空");
            }
            tagLike = "%," + QuestionService.escapeLike(req.tag().strip()) + ",%";
        }

        List<Long> categoryIds = req.categoryIds() == null ? List.of() : req.categoryIds();
        Set<Long> chapters = null;
        Long rootId;
        if (!categoryIds.isEmpty()) {
            for (Long id : categoryIds) {
                if (!tree.exists(id)) {
                    throw BizException.invalid("categoryIds", "分类不存在：" + id);
                }
            }
            chapters = tree.chapterIdsUnder(categoryIds);
            rootId = tree.rootOf(categoryIds.get(0));
        } else if (listMode) {
            // 和错题本 / 收藏列表的默认范围一致：目标考试，没有目标考试时不限（docs/api.md 第 5 节）
            rootId = user.getTargetCategoryId();
            chapters = wrongBookService.scopeChapters(user.getId(), null, tree);
        } else {
            // 随机和标签练习没给分类时，用本人的目标考试
            if (user.getTargetCategoryId() == null) {
                throw BizException.invalid("categoryIds", "请选择章节或先设置目标考试");
            }
            rootId = user.getTargetCategoryId();
            chapters = tree.chapterIdsUnder(rootId);
        }

        List<Long> pool = chapters != null && chapters.isEmpty() ? new ArrayList<>()
                : new ArrayList<>(questionMapper.selectPoolIds(user.getId(),
                        listMode ? mode.name() : null, chapters, req.types(), tagLike, scopeOf(req)));
        Collections.shuffle(pool, ThreadLocalRandom.current());
        int wanted = count != null ? count : Math.min(pool.size(), MAX_LIST_COUNT);
        List<Long> picked = take(pool, wanted, Boolean.TRUE.equals(req.acceptFewer()));
        PracticeSession s = newSession(user.getId(), mode, isRecite(req), req, picked, rootId);
        sessionMapper.insert(s);
        return new Started(view(s, user.getId()), true);
    }

    /** 顺序练习：取整章（含子章节）按顺序排；同一章节已有未完成的就继续那一次。 */
    private Started startSequential(User user, CategoryTree tree, StartRequest req) {
        if (req.categoryIds() == null || req.categoryIds().size() != 1) {
            throw BizException.invalid("categoryIds", "顺序练习需要且只能选一个章节");
        }
        Long categoryId = req.categoryIds().get(0);
        if (!tree.exists(categoryId)) {
            throw BizException.invalid("categoryIds", "分类不存在：" + categoryId);
        }
        boolean recite = isRecite(req);
        PracticeSession existing = sessionMapper.selectOne(new LambdaQueryWrapper<PracticeSession>()
                .eq(PracticeSession::getUserId, user.getId())
                .eq(PracticeSession::getMode, PracticeMode.SEQUENTIAL.name())
                .eq(PracticeSession::getSequentialCategoryId, categoryId)
                .eq(PracticeSession::getReciteMode, recite)
                .eq(PracticeSession::getStatus, STATUS_IN_PROGRESS)
                .orderByDesc(PracticeSession::getStartedAt)
                .last("LIMIT 1"));
        if (existing != null) {
            return new Started(view(existing, user.getId()), false);
        }
        Set<Long> chapters = tree.chapterIdsUnder(categoryId);
        List<Question> questions = chapters.isEmpty() ? List.of()
                : questionMapper.selectList(new LambdaQueryWrapper<Question>()
                        .select(Question::getId, Question::getCategoryId, Question::getSort)
                        .eq(Question::getStatus, QuestionService.STATUS_PUBLISHED)
                        .in(Question::getCategoryId, chapters));
        List<Long> ids = questions.stream()
                .sorted(Comparator.comparingInt((Question q) -> tree.order(q.getCategoryId()))
                        .thenComparing(Question::getSort).thenComparing(Question::getId))
                .map(Question::getId)
                .toList();
        if (ids.isEmpty()) {
            throw poolInsufficient(0);
        }
        PracticeSession s = newSession(user.getId(), PracticeMode.SEQUENTIAL, recite, req, ids,
                tree.rootOf(categoryId));
        s.setSequentialCategoryId(categoryId);
        sessionMapper.insert(s);
        return new Started(view(s, user.getId()), true);
    }

    /** 每日一练：每人每个目标考试每天（北京时间）一组 10 道，优先没做过的题。 */
    private Started startDaily(User user, CategoryTree tree, StartRequest req) {
        Long rootId = user.getTargetCategoryId();
        if (rootId == null || !tree.exists(rootId)) {
            throw BizException.invalid("targetCategoryId", "请先选择目标考试");
        }
        LocalDate today = TimeUtil.todayBeijing(clock);
        PracticeSession existing = findDaily(user.getId(), rootId, today);
        if (existing != null) {
            return new Started(view(existing, user.getId()), false);
        }
        Set<Long> chapters = tree.chapterIdsUnder(rootId);
        List<Long> picked = new ArrayList<>();
        if (!chapters.isEmpty()) {
            List<Long> undone = new ArrayList<>(
                    questionMapper.selectPoolIds(user.getId(), null, chapters, null, null, "UNDONE"));
            List<Long> others = new ArrayList<>(
                    questionMapper.selectPoolIds(user.getId(), null, chapters, null, null, "ALL"));
            others.removeAll(new LinkedHashSet<>(undone));
            Collections.shuffle(undone, ThreadLocalRandom.current());
            Collections.shuffle(others, ThreadLocalRandom.current());
            for (List<Long> source : List.of(undone, others)) {
                for (Long id : source) {
                    if (picked.size() < DAILY_COUNT) {
                        picked.add(id);
                    }
                }
            }
        }
        if (picked.isEmpty()) {
            throw poolInsufficient(0);
        }
        PracticeSession s = newSession(user.getId(), PracticeMode.DAILY, false, req, picked, rootId);
        s.setDailyDate(today);
        try {
            sessionMapper.insert(s);
        } catch (DuplicateKeyException e) {
            // 同一时刻的另一个请求已经建好了今天这一组
            PracticeSession raced = findDaily(user.getId(), rootId, today);
            if (raced == null) {
                throw new BizException(ErrorCode.CONFLICT, "每日一练正在生成，请重试");
            }
            return new Started(view(raced, user.getId()), false);
        }
        return new Started(view(s, user.getId()), true);
    }

    /** 重做某次练习或某场已结算考试里提交了但没全对的题，按原顺序。 */
    private Started startRetry(User user, StartRequest req) {
        if ((req.fromSessionId() == null) == (req.fromExamRecordId() == null)) {
            throw BizException.invalid("fromSessionId", "fromSessionId 和 fromExamRecordId 必须二选一");
        }
        List<Long> ordered;
        String source;
        long sourceId;
        Long rootId;
        if (req.fromSessionId() != null) {
            PracticeSession from = requireOwn(user.getId(), req.fromSessionId());
            ordered = questionIds(from);
            source = SOURCE_PRACTICE;
            sourceId = from.getId();
            rootId = from.getRootCategoryId();
        } else {
            ExamRecord record = examRecordMapper.selectById(req.fromExamRecordId());
            if (record == null || !record.getUserId().equals(user.getId())) {
                throw BizException.notFound("考试记录");
            }
            if (!"FINISHED".equals(record.getStatus())) {
                throw BizException.conflict("考试还没结算");
            }
            ExamSnapshot snapshot = JsonUtil.read(record.getQuestionSnapshot(), ExamSnapshot.class);
            ordered = snapshot.questions().stream()
                    .sorted(Comparator.comparingInt(ExamSnapshot.Item::index))
                    .map(ExamSnapshot.Item::questionId).toList();
            source = SOURCE_EXAM;
            sourceId = record.getId();
            ExamPaper paper = examPaperMapper.selectById(record.getPaperId());
            rootId = paper == null ? null : paper.getCategoryId();
        }
        Set<Long> wrong = new LinkedHashSet<>();
        for (AnswerRecord r : answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                .select(AnswerRecord::getQuestionId)
                .eq(AnswerRecord::getSource, source)
                .eq(AnswerRecord::getSourceId, sourceId)
                .eq(AnswerRecord::getAnswered, true)
                .eq(AnswerRecord::getIsCorrect, false))) {
            wrong.add(r.getQuestionId());
        }
        Map<Long, Question> questions = questionService.loadByIds(wrong);
        List<Long> picked = ordered.stream()
                .filter(id -> wrong.contains(id) && questions.containsKey(id)
                        && QuestionService.STATUS_PUBLISHED.equals(questions.get(id).getStatus()))
                .distinct()
                .toList();
        if (picked.isEmpty()) {
            throw poolInsufficient(0);
        }
        PracticeSession s = newSession(user.getId(), PracticeMode.RETRY, isRecite(req), req, picked, rootId);
        sessionMapper.insert(s);
        return new Started(view(s, user.getId()), true);
    }

    private static List<Long> take(List<Long> pool, int wanted, boolean acceptFewer) {
        if (pool.isEmpty() || (pool.size() < wanted && !acceptFewer)) {
            throw poolInsufficient(pool.size());
        }
        return new ArrayList<>(pool.subList(0, Math.min(wanted, pool.size())));
    }

    private static BizException poolInsufficient(int available) {
        return new BizException(ErrorCode.POOL_INSUFFICIENT,
                available == 0 ? "没有符合条件的题" : "符合条件的题只有 " + available + " 道",
                Map.of("available", available));
    }

    private static String scopeOf(StartRequest req) {
        return req.scope() == null ? "ALL" : req.scope();
    }

    private static boolean isRecite(StartRequest req) {
        return Boolean.TRUE.equals(req.reciteMode());
    }

    private PracticeSession newSession(long userId, PracticeMode mode, boolean recite, StartRequest req,
                                       List<Long> questionIds, Long rootId) {
        LocalDateTime now = TimeUtil.nowUtc(clock);
        return new PracticeSession()
                .setUserId(userId)
                .setMode(mode.name())
                .setReciteMode(recite)
                .setParamsJson(JsonUtil.write(req))
                .setQuestionIds(JsonUtil.write(questionIds))
                .setCurrentIndex(0)
                .setStatus(STATUS_IN_PROGRESS)
                .setRootCategoryId(rootId)
                .setStartedAt(now)
                .setUpdatedAt(now);
    }

    private PracticeSession findDaily(long userId, long rootId, LocalDate day) {
        return sessionMapper.selectOne(new LambdaQueryWrapper<PracticeSession>()
                .eq(PracticeSession::getUserId, userId)
                .eq(PracticeSession::getRootCategoryId, rootId)
                .eq(PracticeSession::getDailyDate, day));
    }

    // ------------------------------------------------------------------ 查询

    public SessionView get(long userId, long sessionId) {
        return view(requireOwn(userId, sessionId), userId);
    }

    /** 最近一次未完成的非每日练习；没有时返回 null。 */
    public SessionView current(long userId) {
        PracticeSession s = sessionMapper.selectOne(new LambdaQueryWrapper<PracticeSession>()
                .eq(PracticeSession::getUserId, userId)
                .eq(PracticeSession::getStatus, STATUS_IN_PROGRESS)
                .ne(PracticeSession::getMode, PracticeMode.DAILY.name())
                .orderByDesc(PracticeSession::getStartedAt)
                .orderByDesc(PracticeSession::getId)
                .last("LIMIT 1"));
        return s == null ? null : view(s, userId);
    }

    public PageResult<SessionListItem> list(long userId, PageQuery pageQuery) {
        Page<PracticeSession> page = pageQuery.toPage();
        sessionMapper.selectPage(page, new LambdaQueryWrapper<PracticeSession>()
                .select(PracticeSession::getId, PracticeSession::getMode, PracticeSession::getQuestionIds,
                        PracticeSession::getStartedAt, PracticeSession::getCompletedAt)
                .eq(PracticeSession::getUserId, userId)
                .orderByDesc(PracticeSession::getStartedAt)
                .orderByDesc(PracticeSession::getId));
        List<Long> ids = page.getRecords().stream().map(PracticeSession::getId).toList();
        Map<Long, AggRow> counts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (AggRow row : answerMapper.countBySources(SOURCE_PRACTICE, ids)) {
                counts.put(row.getGroupId(), row);
            }
        }
        return PageResult.of(page, s -> {
            AggRow c = counts.get(s.getId());
            return new SessionListItem(s.getId(), s.getMode(), questionIds(s).size(),
                    c == null ? 0 : (int) c.total(), c == null ? 0 : (int) c.correct(),
                    TimeUtil.toInstant(s.getStartedAt()), TimeUtil.toInstant(s.getCompletedAt()));
        });
    }

    // ------------------------------------------------------------------ 作答

    @Transactional
    public SubmitResult submit(long userId, long sessionId, SubmitRequest req) {
        PracticeSession s = requireOwn(userId, sessionId);
        List<Long> ids = questionIds(s);
        long questionId = req.questionId();
        if (!ids.contains(questionId)) {
            throw BizException.invalid("questionId", "这道题不在本次练习里");
        }
        if (Boolean.TRUE.equals(s.getReciteMode())) {
            throw BizException.conflict("背题模式不能提交答案");
        }
        Question q = questionMapper.selectById(questionId);
        if (q == null) {
            throw BizException.notFound("题目");
        }
        AnswerRecord existing = findRecord(s.getId(), questionId);
        if (existing != null) {
            return result(existing, q, true);
        }
        if (!STATUS_IN_PROGRESS.equals(s.getStatus())) {
            throw BizException.conflict("这次练习已结束");
        }

        AnswerKey key = AnswerKey.of(q);
        List<String> answer = gradingService.normalize(key.type(), req.userAnswer(), key.blankCount());
        boolean correct = gradingService.grade(key, answer);
        LocalDateTime now = TimeUtil.nowUtc(clock);
        Long rootId = categoryService.tree().rootOf(q.getCategoryId());
        AnswerRecord record = new AnswerRecord()
                .setUserId(userId)
                .setQuestionId(questionId)
                .setSource(SOURCE_PRACTICE)
                .setSourceId(s.getId())
                .setUserAnswer(JsonUtil.write(answer))
                .setAnswered(true)
                .setIsCorrect(correct)
                .setCategoryIdSnapshot(q.getCategoryId())
                .setRootCategoryId(rootId != null ? rootId : s.getRootCategoryId() != null ? s.getRootCategoryId() : 0L)
                .setTimeSpentSec(req.timeSpentSec())
                .setGradedAt(now);
        try {
            answerMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 网络重试或并发重复提交：唯一索引保证只记第一次
            AnswerRecord first = findRecord(s.getId(), questionId);
            if (first == null) {
                throw BizException.conflict("这道题正在提交，请稍后刷新");
            }
            return result(first, q, true);
        }
        wrongBookService.onGraded(userId, questionId, correct, answer,
                PracticeMode.valueOf(s.getMode()).countsWrongStreak());

        long submitted = answerMapper.selectCount(new LambdaQueryWrapper<AnswerRecord>()
                .eq(AnswerRecord::getSource, SOURCE_PRACTICE).eq(AnswerRecord::getSourceId, s.getId()));
        if (submitted >= ids.size()) {
            complete(s.getId(), now);
        }
        return result(record, q, false);
    }

    @Transactional
    public void saveProgress(long userId, long sessionId, int currentIndex) {
        PracticeSession s = requireOwn(userId, sessionId);
        if (currentIndex >= questionIds(s).size()) {
            throw BizException.invalid("currentIndex", "超出题目范围");
        }
        sessionMapper.update(null, new LambdaUpdateWrapper<PracticeSession>()
                .eq(PracticeSession::getId, s.getId())
                .set(PracticeSession::getCurrentIndex, currentIndex)
                .set(PracticeSession::getUpdatedAt, TimeUtil.nowUtc(clock)));
    }

    /** 结束练习。每日一练只有全部提交后才算完成（写 completedAt），否则保持进行中、当天可以继续做。 */
    @Transactional
    public FinishResult finish(long userId, long sessionId) {
        PracticeSession s = requireOwn(userId, sessionId);
        List<Long> ids = questionIds(s);
        List<AnswerRecord> records = records(s.getId());
        LocalDateTime now = TimeUtil.nowUtc(clock);
        boolean allSubmitted = records.size() >= ids.size();
        if (STATUS_IN_PROGRESS.equals(s.getStatus())
                && (allSubmitted || !PracticeMode.DAILY.name().equals(s.getMode()))) {
            complete(s.getId(), now);
            s = sessionMapper.selectById(s.getId());
        }
        LocalDateTime end = s.getCompletedAt() != null ? s.getCompletedAt() : now;
        long duration = Math.max(0, Duration.between(s.getStartedAt(), end).getSeconds());
        int correct = (int) records.stream().filter(r -> Boolean.TRUE.equals(r.getIsCorrect())).count();
        Map<Long, AnswerRecord> byQuestion = byQuestion(records);
        List<Long> wrongIds = ids.stream()
                .filter(id -> byQuestion.containsKey(id) && !Boolean.TRUE.equals(byQuestion.get(id).getIsCorrect()))
                .toList();
        return new FinishResult(duration, records.size(), correct, Rates.rate(correct, records.size()), wrongIds);
    }

    private void complete(long sessionId, LocalDateTime now) {
        sessionMapper.update(null, new LambdaUpdateWrapper<PracticeSession>()
                .eq(PracticeSession::getId, sessionId)
                .eq(PracticeSession::getStatus, STATUS_IN_PROGRESS)
                .set(PracticeSession::getStatus, STATUS_COMPLETED)
                .set(PracticeSession::getCompletedAt, now)
                .set(PracticeSession::getUpdatedAt, now));
    }

    // ------------------------------------------------------------------ 组装

    private SessionView view(PracticeSession s, long userId) {
        List<Long> ids = questionIds(s);
        Map<Long, Question> questions = questionService.loadByIds(ids);
        Map<Long, AnswerRecord> records = byQuestion(records(s.getId()));
        Map<Long, Favorite> favorites = questionService.favoritesOf(userId, ids);
        CategoryTree tree = categoryService.tree();
        boolean recite = Boolean.TRUE.equals(s.getReciteMode());
        List<SessionQuestion> items = new ArrayList<>(ids.size());
        int correct = 0;
        for (Long id : ids) {
            Question q = questions.get(id);
            if (q == null) {
                continue; // 题目已被真删（只有没人做过的题才会真删）
            }
            AnswerRecord r = records.get(id);
            boolean submitted = r != null;
            if (submitted && Boolean.TRUE.equals(r.getIsCorrect())) {
                correct++;
            }
            AnswerView answer = submitted || recite ? assembler.answer(q) : null;
            items.add(new SessionQuestion(assembler.view(q, tree, favorites.get(id)), submitted,
                    submitted ? JsonUtil.read(r.getUserAnswer(), STRING_LIST) : null,
                    submitted ? r.getIsCorrect() : null, answer));
        }
        return new SessionView(s.getId(), s.getMode(), recite, s.getStatus(), ids.size(), records.size(), correct,
                s.getCurrentIndex(), TimeUtil.toInstant(s.getStartedAt()), TimeUtil.toInstant(s.getCompletedAt()),
                s.getDailyDate(), items);
    }

    private SubmitResult result(AnswerRecord r, Question q, boolean already) {
        AnswerKey key = AnswerKey.of(q);
        boolean fill = key.type() == QuestionType.FILL;
        return new SubmitResult(q.getId(), Boolean.TRUE.equals(r.getIsCorrect()),
                JsonUtil.read(r.getUserAnswer(), STRING_LIST),
                fill ? null : key.correctAnswer(), fill ? key.acceptedAnswers() : null,
                assembler.html(q.getAnalysis()), already);
    }

    /** 取本人的练习；不存在或不是本人的都返回 404。 */
    private PracticeSession requireOwn(long userId, long sessionId) {
        PracticeSession s = sessionMapper.selectById(sessionId);
        if (s == null || s.getUserId() != userId) {
            throw BizException.notFound("练习");
        }
        return s;
    }

    private static List<Long> questionIds(PracticeSession s) {
        List<Long> ids = JsonUtil.read(s.getQuestionIds(), LONG_LIST);
        return ids == null ? List.of() : ids;
    }

    private List<AnswerRecord> records(long sessionId) {
        return answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                .eq(AnswerRecord::getSource, SOURCE_PRACTICE).eq(AnswerRecord::getSourceId, sessionId));
    }

    private AnswerRecord findRecord(long sessionId, long questionId) {
        return answerMapper.selectOne(new LambdaQueryWrapper<AnswerRecord>()
                .eq(AnswerRecord::getSource, SOURCE_PRACTICE)
                .eq(AnswerRecord::getSourceId, sessionId)
                .eq(AnswerRecord::getQuestionId, questionId));
    }

    private static Map<Long, AnswerRecord> byQuestion(List<AnswerRecord> records) {
        Map<Long, AnswerRecord> map = new HashMap<>();
        for (AnswerRecord r : records) {
            map.put(r.getQuestionId(), r);
        }
        return map;
    }
}
