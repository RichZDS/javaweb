package com.richzds.quiz.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import com.richzds.quiz.common.BizException;
import com.richzds.quiz.common.JsonUtil;
import com.richzds.quiz.common.Rates;
import com.richzds.quiz.common.TimeUtil;
import com.richzds.quiz.domain.PracticeMode;
import com.richzds.quiz.dto.StatsDtos.CategoryItem;
import com.richzds.quiz.dto.StatsDtos.DailyItem;
import com.richzds.quiz.dto.StatsDtos.ExamItem;
import com.richzds.quiz.dto.StatsDtos.LeaderItem;
import com.richzds.quiz.dto.StatsDtos.Leaderboard;
import com.richzds.quiz.dto.StatsDtos.MyRank;
import com.richzds.quiz.dto.StatsDtos.Overview;
import com.richzds.quiz.dto.StatsDtos.WeakItem;
import com.richzds.quiz.entity.AnswerRecord;
import com.richzds.quiz.entity.Category;
import com.richzds.quiz.entity.ExamPaper;
import com.richzds.quiz.entity.ExamRecord;
import com.richzds.quiz.entity.PracticeSession;
import com.richzds.quiz.entity.User;
import com.richzds.quiz.exam.ExamSettler;
import com.richzds.quiz.exam.ExamSnapshot;
import com.richzds.quiz.mapper.AggRow;
import com.richzds.quiz.mapper.AnswerRecordMapper;
import com.richzds.quiz.mapper.ExamPaperMapper;
import com.richzds.quiz.mapper.ExamRecordMapper;
import com.richzds.quiz.mapper.PracticeSessionMapper;
import com.richzds.quiz.mapper.QuestionMapper;
import com.richzds.quiz.mapper.UserMapper;

/**
 * 统计（docs/api.md 第 7 节）。都按本人；范围默认是目标考试，可用 categoryId 指定一级分类，
 * 两者都没有时不按考试过滤。“作答”只算正式提交且已判分的（answered = TRUE），“哪一天”按北京时间。
 */
@Service
public class StatsService {

    static final int MAX_DAYS = 365;
    static final int MAX_WEAK_LIMIT = 50;
    /** 薄弱点：近 N 天里至少做过这么多道不同的题，章节才参与排序。 */
    static final int MIN_WEAK_SAMPLES = 5;
    static final int LEADERBOARD_SIZE = 20;

    private final AnswerRecordMapper answerMapper;
    private final PracticeSessionMapper sessionMapper;
    private final ExamRecordMapper examRecordMapper;
    private final ExamPaperMapper paperMapper;
    private final QuestionMapper questionMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final CategoryService categoryService;
    private final Clock clock;

    public StatsService(AnswerRecordMapper answerMapper, PracticeSessionMapper sessionMapper,
                        ExamRecordMapper examRecordMapper, ExamPaperMapper paperMapper, QuestionMapper questionMapper,
                        UserMapper userMapper, UserService userService, CategoryService categoryService, Clock clock) {
        this.answerMapper = answerMapper;
        this.sessionMapper = sessionMapper;
        this.examRecordMapper = examRecordMapper;
        this.paperMapper = paperMapper;
        this.questionMapper = questionMapper;
        this.userMapper = userMapper;
        this.userService = userService;
        this.categoryService = categoryService;
        this.clock = clock;
    }

    public Overview overview(long userId, Long categoryId) {
        Long root = resolveRoot(userId, categoryId);
        AggRow all = answerMapper.countAnswers(userId, root, null);
        AggRow today = answerMapper.countAnswers(userId, root,
                TimeUtil.beijingDayStartUtc(TimeUtil.todayBeijing(clock)));
        long exams = finishedExams(userId, root, null).size();
        return new Overview(all.total(), all.correct(), Rates.rate(all.correct(), all.total()),
                answerMapper.countStudyDays(userId, root), streakDays(userId, root), today.total(), exams);
    }

    /**
     * 连续完成每日一练的天数：从今天（今天没完成就从昨天）往前数，到第一天没完成为止。
     * “完成”指那一组 10 道全部提交（completed_at 有值），日期取那一组所属的北京时间日期。
     */
    int streakDays(long userId, Long root) {
        Set<LocalDate> done = new HashSet<>();
        for (PracticeSession s : sessionMapper.selectList(new LambdaQueryWrapper<PracticeSession>()
                .select(PracticeSession::getDailyDate)
                .eq(PracticeSession::getUserId, userId)
                .eq(PracticeSession::getMode, PracticeMode.DAILY.name())
                .eq(root != null, PracticeSession::getRootCategoryId, root)
                .isNotNull(PracticeSession::getCompletedAt))) {
            done.add(s.getDailyDate());
        }
        LocalDate day = TimeUtil.todayBeijing(clock);
        if (!done.contains(day)) {
            day = day.minusDays(1);
        }
        int streak = 0;
        while (done.contains(day)) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }

    /** 近 days 天（含今天）每天的作答，没作答的日子也给出，从早到晚。 */
    public List<DailyItem> daily(long userId, Long categoryId, Integer days) {
        int n = checkDays(days);
        Long root = resolveRoot(userId, categoryId);
        LocalDate today = TimeUtil.todayBeijing(clock);
        LocalDate first = today.minusDays(n - 1L);
        Map<LocalDate, AggRow> byDay = new HashMap<>();
        for (AggRow row : answerMapper.dailyCounts(userId, root, TimeUtil.beijingDayStartUtc(first))) {
            byDay.put(row.getStatDay(), row);
        }
        List<DailyItem> out = new ArrayList<>(n);
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            AggRow row = byDay.get(d);
            long total = row == null ? 0 : row.total();
            long correct = row == null ? 0 : row.correct();
            out.add(new DailyItem(d, total, correct, Rates.rate(correct, total)));
        }
        return out;
    }

    /** 范围内每个章节（按分类树顺序），没做过的章节也列出。章节按作答时所在章节归属。 */
    public List<CategoryItem> categories(long userId, Long categoryId) {
        Long root = resolveRoot(userId, categoryId);
        CategoryTree tree = categoryService.tree();
        List<Long> chapters = new ArrayList<>();
        for (Category r : tree.roots()) {
            if (root == null || r.getId().equals(root)) {
                chapters.addAll(tree.chapterIdsUnder(r.getId()));
            }
        }
        Map<Long, Long> questionCounts = new HashMap<>();
        for (AggRow row : questionMapper.countPublishedByCategory()) {
            questionCounts.put(row.getGroupId(), row.total());
        }
        Map<Long, AggRow> answered = new HashMap<>();
        for (AggRow row : answerMapper.countByChapter(userId, root)) {
            answered.put(row.getGroupId(), row);
        }
        return chapters.stream().map(id -> {
            AggRow a = answered.get(id);
            long total = a == null ? 0 : a.total();
            long correct = a == null ? 0 : a.correct();
            return new CategoryItem(id, tree.path(id), questionCounts.getOrDefault(id, 0L),
                    a == null ? 0 : a.distinct(), total, Rates.rate(correct, total));
        }).toList();
    }

    /**
     * 薄弱点：近 days 天里每道题只看最后一次作答，按那次作答所在章节汇总，
     * 至少 5 道不同题的章节按正确率升序（同率时样本多的在前），取前 limit 个。
     */
    public List<WeakItem> weak(long userId, Long categoryId, Integer days, Integer limit) {
        int n = checkDays(days);
        int max = limit == null ? 5 : limit;
        if (max < 1 || max > MAX_WEAK_LIMIT) {
            throw BizException.invalid("limit", "只能是 1–" + MAX_WEAK_LIMIT);
        }
        Long root = resolveRoot(userId, categoryId);
        LocalDateTime from = TimeUtil.beijingDayStartUtc(TimeUtil.todayBeijing(clock).minusDays(n - 1L));
        Map<Long, AnswerRecord> lastByQuestion = new LinkedHashMap<>();
        for (AnswerRecord r : answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                .select(AnswerRecord::getId, AnswerRecord::getQuestionId, AnswerRecord::getCategoryIdSnapshot,
                        AnswerRecord::getIsCorrect, AnswerRecord::getGradedAt)
                .eq(AnswerRecord::getUserId, userId)
                .eq(AnswerRecord::getAnswered, true)
                .eq(root != null, AnswerRecord::getRootCategoryId, root)
                .ge(AnswerRecord::getGradedAt, from)
                .orderByAsc(AnswerRecord::getGradedAt).orderByAsc(AnswerRecord::getId))) {
            lastByQuestion.put(r.getQuestionId(), r);
        }
        Map<Long, int[]> byChapter = new HashMap<>();
        for (AnswerRecord r : lastByQuestion.values()) {
            int[] c = byChapter.computeIfAbsent(r.getCategoryIdSnapshot(), k -> new int[2]);
            c[0]++;
            if (Boolean.TRUE.equals(r.getIsCorrect())) {
                c[1]++;
            }
        }
        CategoryTree tree = categoryService.tree();
        return byChapter.entrySet().stream()
                .filter(e -> e.getValue()[0] >= MIN_WEAK_SAMPLES)
                .map(e -> new WeakItem(e.getKey(), tree.path(e.getKey()), e.getValue()[0],
                        Rates.rate(e.getValue()[1], e.getValue()[0])))
                .sorted(Comparator.comparing(WeakItem::correctRate)
                        .thenComparing(Comparator.comparingInt(WeakItem::sampleCount).reversed())
                        .thenComparingInt(w -> tree.order(w.categoryId())))
                .limit(max)
                .toList();
    }

    /** 已结算的考试成绩，按交卷时间从早到晚（画折线用）。 */
    public List<ExamItem> exams(long userId, Long categoryId, Long paperId) {
        Long root = resolveRoot(userId, categoryId);
        return finishedExams(userId, root, paperId).stream()
                .sorted(Comparator.comparing(ExamRecord::getSubmittedAt).thenComparing(ExamRecord::getId))
                .map(r -> {
                    int score = r.getScore() == null ? 0 : r.getScore();
                    ExamSnapshot snap = JsonUtil.read(r.getQuestionSnapshot(), ExamSnapshot.class);
                    return new ExamItem(r.getId(), r.getPaperId(), snap.title(), score, r.getTotalScore(),
                            r.getPassScore(), Rates.rate(score, r.getTotalScore()),
                            TimeUtil.toInstant(r.getSubmittedAt()));
                })
                .toList();
    }

    private List<ExamRecord> finishedExams(long userId, Long root, Long paperId) {
        List<ExamRecord> records = examRecordMapper.selectList(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getUserId, userId)
                .eq(ExamRecord::getStatus, ExamSettler.STATUS_FINISHED)
                .eq(paperId != null, ExamRecord::getPaperId, paperId));
        if (root == null || records.isEmpty()) {
            return records;
        }
        Set<Long> paperIds = new HashSet<>();
        records.forEach(r -> paperIds.add(r.getPaperId()));
        Set<Long> inRoot = new HashSet<>();
        for (ExamPaper p : paperMapper.selectBatchIds(paperIds)) {
            if (root.equals(p.getCategoryId())) {
                inRoot.add(p.getId());
            }
        }
        return records.stream().filter(r -> inRoot.contains(r.getPaperId())).toList();
    }

    /**
     * 本周（北京时间周一 00:00 起）作答次数前 20。并列同名次（1、2、2、4），只算正常状态的用户；
     * 范围和其他统计一样默认按目标考试。me 是本人的名次，本周没作答时 rank 为 null。
     */
    public Leaderboard leaderboard(long userId, Long categoryId, String period) {
        if (period != null && !period.isBlank() && !"WEEK".equals(period.strip())) {
            throw BizException.invalid("period", "目前只支持 WEEK");
        }
        Long root = resolveRoot(userId, categoryId);
        LocalDate monday = TimeUtil.todayBeijing(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<AggRow> rows = answerMapper.leaderboard(root, TimeUtil.beijingDayStartUtc(monday));
        List<Long> topIds = rows.stream().limit(LEADERBOARD_SIZE).map(AggRow::getGroupId).toList();
        Map<Long, User> users = new HashMap<>();
        if (!topIds.isEmpty()) {
            userMapper.selectBatchIds(topIds).forEach(u -> users.put(u.getId(), u));
        }
        List<LeaderItem> items = new ArrayList<>();
        MyRank me = new MyRank(null, 0);
        int rank = 0;
        long previous = -1;
        for (int i = 0; i < rows.size(); i++) {
            AggRow row = rows.get(i);
            if (row.total() != previous) {
                rank = i + 1;
                previous = row.total();
            }
            if (i < LEADERBOARD_SIZE) {
                User u = users.get(row.getGroupId());
                items.add(new LeaderItem(rank, u == null ? "" : u.getNickname(), u == null ? null : u.getAvatarUrl(),
                        row.total()));
            }
            if (row.getGroupId() == userId) {
                me = new MyRank(rank, row.total());
            }
        }
        return new Leaderboard(items, me);
    }

    /** 统计范围：指定的一级分类，否则本人目标考试，都没有时为 null（不过滤）。 */
    private Long resolveRoot(long userId, Long categoryId) {
        if (categoryId != null) {
            Category c = categoryService.tree().get(categoryId);
            if (c == null || c.getLevel() != CategoryTree.LEVEL_EXAM) {
                throw BizException.invalid("categoryId", "必须是一级分类（考试）");
            }
            return categoryId;
        }
        return userService.requireUser(userId).getTargetCategoryId();
    }

    private static int checkDays(Integer days) {
        int n = days == null ? 30 : days;
        if (n < 1 || n > MAX_DAYS) {
            throw BizException.invalid("days", "只能是 1–" + MAX_DAYS);
        }
        return n;
    }
}
