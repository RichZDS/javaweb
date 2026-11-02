package com.richzds.quiz.admin;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.richzds.quiz.common.BizException;
import com.richzds.quiz.common.PageQuery;
import com.richzds.quiz.common.PageResult;
import com.richzds.quiz.common.Rates;
import com.richzds.quiz.common.TimeUtil;
import com.richzds.quiz.dto.AdminDtos.AdminUser;
import com.richzds.quiz.dto.AdminDtos.Dashboard;
import com.richzds.quiz.dto.AdminDtos.DashboardDay;
import com.richzds.quiz.dto.AdminDtos.DashboardPaper;
import com.richzds.quiz.dto.AdminDtos.FeedbackHandle;
import com.richzds.quiz.dto.AdminDtos.FeedbackItem;
import com.richzds.quiz.entity.ExamPaper;
import com.richzds.quiz.entity.Question;
import com.richzds.quiz.entity.QuestionFeedback;
import com.richzds.quiz.entity.User;
import com.richzds.quiz.mapper.AggRow;
import com.richzds.quiz.mapper.AnswerRecordMapper;
import com.richzds.quiz.mapper.ExamPaperMapper;
import com.richzds.quiz.mapper.ExamRecordMapper;
import com.richzds.quiz.mapper.QuestionFeedbackMapper;
import com.richzds.quiz.mapper.QuestionMapper;
import com.richzds.quiz.mapper.UserMapper;
import com.richzds.quiz.service.CategoryService;
import com.richzds.quiz.service.CategoryTree;
import com.richzds.quiz.service.QuestionAssembler;
import com.richzds.quiz.service.QuestionService;
import com.richzds.quiz.service.UserService;

/** 后台的用户管理、纠错处理和数据看板。 */
@Service
public class AdminSystemService {

    static final int DASHBOARD_DAYS = 14;
    private static final List<String> FEEDBACK_STATUSES = List.of("PENDING", "RESOLVED", "REJECTED");

    private final UserMapper userMapper;
    private final AnswerRecordMapper answerMapper;
    private final QuestionMapper questionMapper;
    private final QuestionFeedbackMapper feedbackMapper;
    private final ExamPaperMapper paperMapper;
    private final ExamRecordMapper recordMapper;
    private final CategoryService categoryService;
    private final QuestionService questionService;
    private final QuestionAssembler assembler;
    private final Clock clock;

    public AdminSystemService(UserMapper userMapper, AnswerRecordMapper answerMapper, QuestionMapper questionMapper,
                              QuestionFeedbackMapper feedbackMapper, ExamPaperMapper paperMapper,
                              ExamRecordMapper recordMapper, CategoryService categoryService,
                              QuestionService questionService, QuestionAssembler assembler, Clock clock) {
        this.userMapper = userMapper;
        this.answerMapper = answerMapper;
        this.questionMapper = questionMapper;
        this.feedbackMapper = feedbackMapper;
        this.paperMapper = paperMapper;
        this.recordMapper = recordMapper;
        this.categoryService = categoryService;
        this.questionService = questionService;
        this.assembler = assembler;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ 用户

    public PageResult<AdminUser> users(String keyword, PageQuery pageQuery) {
        String kw = keyword == null ? "" : keyword.strip();
        if (kw.length() > 50) {
            throw BizException.invalid("keyword", "最多 50 字");
        }
        String like = "%" + QuestionService.escapeLike(kw) + "%";
        Page<User> page = pageQuery.toPage();
        userMapper.selectPage(page, new LambdaQueryWrapper<User>()
                .apply(!kw.isEmpty(), "(username LIKE {0} OR nickname LIKE {1})", like, like)
                .orderByDesc(User::getId));
        Map<Long, Long> counts = new HashMap<>();
        List<Long> ids = page.getRecords().stream().map(User::getId).toList();
        if (!ids.isEmpty()) {
            answerMapper.countByUsers(ids).forEach(r -> counts.put(r.getGroupId(), r.total()));
        }
        return PageResult.of(page, u -> adminUser(u, counts.getOrDefault(u.getId(), 0L)));
    }

    /** 启用或禁用用户；不能禁用自己。禁用立即生效（鉴权每次请求都查库）。 */
    @Transactional
    public AdminUser changeUserStatus(long operatorId, long userId, String status) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw BizException.notFound("用户");
        }
        if (operatorId == userId && !UserService.STATUS_ACTIVE.equals(status)) {
            throw BizException.conflict("不能禁用自己");
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, userId)
                .set(User::getStatus, status).set(User::getUpdatedAt, TimeUtil.nowUtc(clock)));
        u.setStatus(status);
        long answers = answerMapper.countByUsers(List.of(userId)).stream().mapToLong(AggRow::total).sum();
        return adminUser(u, answers);
    }

    private static AdminUser adminUser(User u, long answers) {
        return new AdminUser(u.getId(), u.getUsername(), u.getNickname(), u.getRole(), u.getStatus(), answers,
                TimeUtil.toInstant(u.getLastActiveAt()), TimeUtil.toInstant(u.getCreatedAt()));
    }

    // ------------------------------------------------------------------ 纠错

    public PageResult<FeedbackItem> feedback(String status, PageQuery pageQuery) {
        if (status != null && !status.isBlank() && !FEEDBACK_STATUSES.contains(status)) {
            throw BizException.invalid("status", "只能是 PENDING、RESOLVED 或 REJECTED");
        }
        Page<QuestionFeedback> page = pageQuery.toPage();
        feedbackMapper.selectPage(page, new LambdaQueryWrapper<QuestionFeedback>()
                .eq(status != null && !status.isBlank(), QuestionFeedback::getStatus, status)
                .orderByDesc(QuestionFeedback::getCreatedAt).orderByDesc(QuestionFeedback::getId));
        List<QuestionFeedback> rows = page.getRecords();
        Map<Long, Question> questions = questionService.loadByIds(
                rows.stream().map(QuestionFeedback::getQuestionId).distinct().toList());
        Map<Long, User> users = new HashMap<>();
        List<Long> userIds = rows.stream().map(QuestionFeedback::getUserId).distinct().toList();
        if (!userIds.isEmpty()) {
            userMapper.selectBatchIds(userIds).forEach(u -> users.put(u.getId(), u));
        }
        CategoryTree tree = categoryService.tree();
        return PageResult.of(page, f -> feedbackItem(f, questions.get(f.getQuestionId()), users.get(f.getUserId()),
                tree));
    }

    @Transactional
    public FeedbackItem handleFeedback(long id, FeedbackHandle req) {
        QuestionFeedback f = feedbackMapper.selectById(id);
        if (f == null) {
            throw BizException.notFound("纠错");
        }
        String reply = req.reply() == null || req.reply().isBlank() ? null : req.reply().strip();
        f.setStatus(req.status()).setReply(reply).setHandledAt(TimeUtil.nowUtc(clock));
        feedbackMapper.update(null, new LambdaUpdateWrapper<QuestionFeedback>().eq(QuestionFeedback::getId, id)
                .set(QuestionFeedback::getStatus, f.getStatus())
                .set(QuestionFeedback::getReply, reply)
                .set(QuestionFeedback::getHandledAt, f.getHandledAt()));
        return feedbackItem(f, questionMapper.selectById(f.getQuestionId()), userMapper.selectById(f.getUserId()),
                categoryService.tree());
    }

    private FeedbackItem feedbackItem(QuestionFeedback f, Question q, User u, CategoryTree tree) {
        return new FeedbackItem(f.getId(), f.getQuestionId(), q == null ? "" : assembler.preview(q.getStem()),
                q == null ? "" : tree.path(q.getCategoryId()), f.getType(), q == null ? null : q.getType(),
                f.getContent(),
                f.getStatus(), f.getReply(), u == null ? null : u.getUsername(), TimeUtil.toInstant(f.getCreatedAt()),
                TimeUtil.toInstant(f.getHandledAt()));
    }

    // ------------------------------------------------------------------ 看板

    /**
     * userCount 数学员（普通用户），questionCount 数已发布的题，paperCount 数全部试卷；
     * daily 为近 14 天（北京时间，含今天）的作答条数和有作答的人数；papers 按已结算场次统计平均分和及格率。
     */
    public Dashboard dashboard() {
        LocalDate today = TimeUtil.todayBeijing(clock);
        LocalDate first = today.minusDays(DASHBOARD_DAYS - 1L);
        Map<LocalDate, AggRow> byDay = new HashMap<>();
        answerMapper.dailyActivity(TimeUtil.beijingDayStartUtc(first)).forEach(r -> byDay.put(r.getStatDay(), r));
        List<DashboardDay> daily = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            AggRow r = byDay.get(d);
            daily.add(new DashboardDay(d, r == null ? 0 : r.total(), r == null ? 0 : r.distinct()));
        }
        Map<Long, AggRow> finished = new HashMap<>();
        recordMapper.finishedStatsByPaper().forEach(r -> finished.put(r.getGroupId(), r));
        List<DashboardPaper> papers = paperMapper.selectList(new LambdaQueryWrapper<ExamPaper>()
                        .orderByAsc(ExamPaper::getId))
                .stream().map(p -> {
                    AggRow r = finished.get(p.getId());
                    long attempts = r == null ? 0 : r.total();
                    Double avg = attempts == 0 ? null : Math.round(r.scores() * 100.0 / attempts) / 100.0;
                    return new DashboardPaper(p.getId(), p.getTitle(), attempts, avg,
                            Rates.rate(r == null ? 0 : r.passed(), attempts));
                }).toList();
        AggRow todayCount = byDay.get(today);
        return new Dashboard(
                userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, UserService.ROLE_USER)),
                questionMapper.selectCount(new LambdaQueryWrapper<Question>()
                        .eq(Question::getStatus, QuestionService.STATUS_PUBLISHED)),
                paperMapper.selectCount(null),
                todayCount == null ? 0 : todayCount.total(),
                feedbackMapper.selectCount(new LambdaQueryWrapper<QuestionFeedback>()
                        .eq(QuestionFeedback::getStatus, "PENDING")),
                daily, papers);
    }
}
