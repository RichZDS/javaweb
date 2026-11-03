package com.richzds.quiz.mock;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.richzds.quiz.admin.AdminPaperService;
import com.richzds.quiz.admin.AdminQuestionService;
import com.richzds.quiz.admin.AdminSystemService;
import com.richzds.quiz.common.BizException;
import com.richzds.quiz.common.TimeUtil;
import com.richzds.quiz.domain.AnswerKey;
import com.richzds.quiz.domain.OptionItem;
import com.richzds.quiz.domain.QuestionType;
import com.richzds.quiz.dto.AdminDtos.AdminPaper;
import com.richzds.quiz.dto.AdminDtos.AdminQuestion;
import com.richzds.quiz.dto.AdminDtos.DeleteResult;
import com.richzds.quiz.dto.AdminDtos.FeedbackHandle;
import com.richzds.quiz.dto.AdminDtos.OptionInput;
import com.richzds.quiz.dto.AdminDtos.PaperInput;
import com.richzds.quiz.dto.AdminDtos.PaperRuleInput;
import com.richzds.quiz.dto.AdminDtos.QuestionInput;
import com.richzds.quiz.dto.AuthDtos.AuthResult;
import com.richzds.quiz.dto.AuthDtos.RegisterRequest;
import com.richzds.quiz.dto.AuthDtos.UpdateMeRequest;
import com.richzds.quiz.dto.ExamDtos.ExamSheet;
import com.richzds.quiz.dto.ExamDtos.PaperItem;
import com.richzds.quiz.dto.ExamDtos.SaveAnswerRequest;
import com.richzds.quiz.dto.ExamDtos.SheetQuestion;
import com.richzds.quiz.dto.PracticeDtos.SessionQuestion;
import com.richzds.quiz.dto.PracticeDtos.SessionView;
import com.richzds.quiz.dto.PracticeDtos.StartRequest;
import com.richzds.quiz.dto.PracticeDtos.SubmitRequest;
import com.richzds.quiz.dto.PracticeDtos.SubmitResult;
import com.richzds.quiz.dto.QuestionDtos.FeedbackRequest;
import com.richzds.quiz.dto.QuestionDtos.FeedbackView;
import com.richzds.quiz.entity.Category;
import com.richzds.quiz.entity.Question;
import com.richzds.quiz.entity.User;
import com.richzds.quiz.exam.ExamService;
import com.richzds.quiz.exam.ExamSettlementJob;
import com.richzds.quiz.mapper.CategoryMapper;
import com.richzds.quiz.mapper.QuestionMapper;
import com.richzds.quiz.mapper.UserMapper;
import com.richzds.quiz.mock.MockData.MockPaper;
import com.richzds.quiz.mock.MockData.MockQuestion;
import com.richzds.quiz.mock.MockData.MockUser;
import com.richzds.quiz.mock.MockPlanner.DayPlan;
import com.richzds.quiz.mock.MockPlanner.UserPlan;
import com.richzds.quiz.service.CategoryService;
import com.richzds.quiz.service.CategoryTree;
import com.richzds.quiz.service.FavoriteService;
import com.richzds.quiz.service.FileStorageService;
import com.richzds.quiz.service.PracticeService;
import com.richzds.quiz.service.QuestionAssembler;
import com.richzds.quiz.service.QuestionService;
import com.richzds.quiz.service.UserService;

/**
 * 演示数据模拟器（docs/mock-data.md 第 6 节）：按计划逐天推进时钟，调用真实的业务代码生成全部记录。
 *
 * <p>所有动作（学员的每一次提交、管理员的每一个操作、定时结算）都是一个带时间的事件，放在一个按时间排序的队列里
 * 依次执行，时钟只往前走：不同学员的学习时段可以重叠，管理员当天新发布的题也不会在发布之前被抽到。
 * 单线程运行；随机数都来自同一个种子，同一份数据、同一个种子生成的结果一样。
 */
@Component
@ConditionalOnProperty(name = "app.mock.enabled", havingValue = "true")
public class MockSimulator {

    private static final Logger log = LoggerFactory.getLogger(MockSimulator.class);
    private static final Duration ACTIVE_WRITE_INTERVAL = Duration.ofMinutes(5);
    /** 难度 1–5 对正确率的修正（docs/mock-data.md：难度 1 约 +0.15，难度 5 约 −0.2）。 */
    private static final double[] DIFFICULTY = {0, 0.15, 0.07, 0, -0.1, -0.2};
    private static final String[] FEEDBACK_TYPES = {"ANSWER_WRONG", "STEM_WRONG", "ANALYSIS_UNCLEAR", "OTHER"};
    private static final double[] FEEDBACK_WEIGHTS = {0.3, 0.2, 0.35, 0.15};
    /** 每日一练固定 10 题（和 PracticeService.DAILY_COUNT 一致）。 */
    static final int DAILY_COUNT = 10;

    private final UserService userService;
    private final PracticeService practiceService;
    private final ExamService examService;
    private final ExamSettlementJob settlementJob;
    private final FavoriteService favoriteService;
    private final QuestionService questionService;
    private final QuestionAssembler assembler;
    private final FileStorageService fileStorage;
    private final AdminQuestionService adminQuestions;
    private final AdminPaperService adminPapers;
    private final AdminSystemService adminSystem;
    private final CategoryService categoryService;
    private final CategoryMapper categoryMapper;
    private final QuestionMapper questionMapper;
    private final UserMapper userMapper;

    public MockSimulator(UserService userService, PracticeService practiceService, ExamService examService,
                         ExamSettlementJob settlementJob, FavoriteService favoriteService,
                         QuestionService questionService, QuestionAssembler assembler, FileStorageService fileStorage,
                         AdminQuestionService adminQuestions, AdminPaperService adminPapers,
                         AdminSystemService adminSystem, CategoryService categoryService, CategoryMapper categoryMapper,
                         QuestionMapper questionMapper, UserMapper userMapper) {
        this.userService = userService;
        this.practiceService = practiceService;
        this.examService = examService;
        this.settlementJob = settlementJob;
        this.favoriteService = favoriteService;
        this.questionService = questionService;
        this.assembler = assembler;
        this.fileStorage = fileStorage;
        this.adminQuestions = adminQuestions;
        this.adminPapers = adminPapers;
        this.adminSystem = adminSystem;
        this.categoryService = categoryService;
        this.categoryMapper = categoryMapper;
        this.questionMapper = questionMapper;
        this.userMapper = userMapper;
    }

    /** 这次运行做了什么（计数），结束汇总里打印。 */
    public static final class Stats {
        final Map<String, Long> actions = new TreeMap<>();
        final Map<String, Long> skipped = new TreeMap<>();
        long droppedAfterUntil;

        void count(String what) {
            actions.merge(what, 1L, Long::sum);
        }

        void skip(String what, BizException e) {
            skipped.merge(what + "：" + e.getMessage(), 1L, Long::sum);
        }

        public Map<String, Long> actions() {
            return actions;
        }

        public Map<String, Long> skipped() {
            return skipped;
        }

        public long droppedAfterUntil() {
            return droppedAfterUntil;
        }
    }

    /** 运行一次。clock 是业务用的同一个 {@link MockClock}；until 之后的事件丢掉，不会生成未来的记录。 */
    public Stats run(MockData data, MockPlanner.Plan plan, MockClock clock, Instant until, String password,
                     long adminId) {
        return new Run(data, plan, clock, until, password, adminId).execute();
    }

    // ================================================================== 一次运行

    private final class Run {

        final MockData data;
        final MockPlanner.Plan plan;
        final MockClock clock;
        final Instant until;
        final String password;
        final long adminId;
        final Random rnd;
        final Stats stats = new Stats();
        final PriorityQueue<Event> queue = new PriorityQueue<>(
                Comparator.comparing((Event e) -> e.at).thenComparingLong(e -> e.order));
        long order;
        final List<Student> students = new ArrayList<>();
        final Map<Long, Question> questionCache = new HashMap<>();
        final Map<Long, String> subjectOfChapter = new HashMap<>();
        final Map<Long, String> chapterName = new HashMap<>();
        final Map<Long, List<Long>> subjectsOfRoot = new HashMap<>();
        final Map<Long, List<Long>> chaptersOfRoot = new HashMap<>();
        final Map<String, Category> paths;
        Instant adminLastTouch;
        long startNanos;

        Run(MockData data, MockPlanner.Plan plan, MockClock clock, Instant until, String password, long adminId) {
            this.data = data;
            this.plan = plan;
            this.clock = clock;
            this.until = until;
            this.password = password;
            this.adminId = adminId;
            this.rnd = new Random(data.seed() ^ 0x5DEECE66DL);
            List<Category> all = categoryMapper.selectList(null);
            CategoryTree tree = new CategoryTree(all);
            this.paths = MockData.byPath(tree, all);
            all.sort(Comparator.comparingInt(c -> tree.order(c.getId())));
            for (Category c : all) {
                if (c.getLevel() == CategoryTree.LEVEL_SUBJECT) {
                    subjectsOfRoot.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c.getId());
                } else if (c.getLevel() == CategoryTree.LEVEL_CHAPTER) {
                    Category subject = tree.get(c.getParentId());
                    subjectOfChapter.put(c.getId(), subject.getName());
                    chapterName.put(c.getId(), c.getName());
                    chaptersOfRoot.computeIfAbsent(subject.getParentId(), k -> new ArrayList<>()).add(c.getId());
                }
            }
            for (UserPlan up : plan.users()) {
                students.add(new Student(up));
            }
        }

        Stats execute() {
            startNanos = System.nanoTime();
            Map<LocalDate, List<Student>> disableOn = new HashMap<>();
            for (Student s : students) {
                if (Boolean.TRUE.equals(s.spec.disabled())) {
                    LocalDate day = s.plan.lastDay().plusDays(1 + rnd.nextInt(7));
                    disableOn.computeIfAbsent(day.isAfter(plan.until()) ? plan.until() : day, k -> new ArrayList<>())
                            .add(s);
                }
            }
            LocalDate lastLog = null;
            for (LocalDate day = plan.start(); !day.isAfter(plan.until()); day = day.plusDays(1)) {
                scheduleAdmin(day, disableOn.getOrDefault(day, List.of()));
                scheduleStudents(day);
                drain(dayStart(day.plusDays(1)));
                if (lastLog == null || day.getDayOfMonth() == 1 || day.getDayOfWeek().getValue() == 7) {
                    lastLog = day;
                    log.info("模拟到 {}：{} 人已注册，提交 {} 道，考试 {} 场，用时 {} 秒", day,
                            students.stream().filter(s -> s.id != null).count(),
                            stats.actions.getOrDefault("练习提交", 0L), stats.actions.getOrDefault("开考", 0L),
                            (System.nanoTime() - startNanos) / 1_000_000_000);
                }
            }
            drain(until.plusNanos(1));
            stats.droppedAfterUntil = queue.size();
            queue.clear();
            clock.set(until);
            return stats;
        }

        /** 依次执行时间早于 limit（且不晚于 until）的事件。 */
        void drain(Instant limit) {
            while (!queue.isEmpty()) {
                Event e = queue.peek();
                if (!e.at.isBefore(limit) || e.at.isAfter(until)) {
                    return;
                }
                queue.poll();
                clock.set(e.at);
                Instant next = e.run();
                if (next != null) {
                    e.at = next.isBefore(e.at) ? e.at : next;
                    e.order = order++;
                    queue.add(e);
                }
            }
        }

        void schedule(Instant at, Event e) {
            e.at = at;
            e.order = order++;
            queue.add(e);
        }

        void once(Instant at, Runnable action) {
            schedule(at, new Event() {
                @Override
                Instant run() {
                    action.run();
                    return null;
                }
            });
        }

        Instant now() {
            return clock.instant();
        }

        // ------------------------------------------------------------------ 时间

        Instant dayStart(LocalDate day) {
            return TimeUtil.toInstant(TimeUtil.beijingDayStartUtc(day));
        }

        /** 北京时间 day 的 hour 点附近（±30 分钟内随机）。 */
        Instant around(LocalDate day, int hour) {
            long minute = hour * 60L - 30 + rnd.nextInt(60);
            minute = Math.max(0, Math.min(minute, 24 * 60 - 1));
            return dayStart(day).plusSeconds(minute * 60 + rnd.nextInt(60));
        }

        /** 管理员上班时间：北京时间 9:00–17:59 随机。 */
        Instant workHours(LocalDate day) {
            return dayStart(day).plusSeconds(9 * 3600L + rnd.nextInt(9 * 3600));
        }

        LocalDate today() {
            return TimeUtil.todayBeijing(clock);
        }

        // ------------------------------------------------------------------ 管理员

        void scheduleAdmin(LocalDate day, List<Student> disables) {
            for (MockQuestion q : data.questions()) {
                if (day.equals(q.createdOn())) {
                    once(workHours(day), () -> addQuestion(q));
                }
            }
            for (MockPaper p : data.papers()) {
                if (day.equals(p.createdOn())) {
                    once(workHours(day), () -> addPaper(p));
                }
            }
            for (Student s : disables) {
                once(workHours(day), () -> {
                    if (s.id == null) {
                        return;
                    }
                    touchAdmin();
                    adminSystem.changeUserStatus(adminId, s.id, "DISABLED");
                    s.disabled = true;
                    stats.count("管理员禁用账号");
                });
            }
        }

        void addQuestion(MockQuestion q) {
            touchAdmin();
            List<OptionInput> options = MockData.options(q).stream().map(o -> new OptionInput(o.key(), o.text()))
                    .toList();
            boolean draft = "DRAFT".equals(q.status());
            AdminQuestion created = adminQuestions.create(new QuestionInput(paths.get(q.chapter()).getId(), q.type(),
                    q.stem(), options.isEmpty() ? null : options, MockData.correct(q), MockData.accepted(q),
                    q.fillOptions(), q.analysis(), q.difficulty(), q.tags(), q.imageUrl(),
                    q.source() == null ? "原创" : q.source(), null, draft ? "DRAFT" : "PUBLISHED"));
            stats.count(draft ? "管理员加题（草稿）" : "管理员加题（发布）");
            if ("ARCHIVED".equals(q.status())) {
                LocalDate day = q.createdOn().plusDays(20 + rnd.nextInt(21));
                if (!day.isBefore(plan.until())) {
                    day = plan.until().minusDays(1).isBefore(q.createdOn()) ? q.createdOn() : plan.until().minusDays(1);
                }
                once(workHours(day), () -> {
                    touchAdmin();
                    DeleteResult r = adminQuestions.delete(created.id());
                    stats.count(r.archived() ? "管理员下架旧题（归档）" : "管理员删除旧题（没人做过）");
                });
            }
        }

        void addPaper(MockPaper p) {
            touchAdmin();
            List<PaperRuleInput> rules = p.rules().stream()
                    .map(r -> new PaperRuleInput(paths.get(r.category()).getId(), r.type(), r.count(), r.score()))
                    .toList();
            AdminPaper created = adminPapers.create(new PaperInput(p.title(), paths.get(p.exam()).getId(),
                    p.description(), "RULE", p.durationMin(), p.passScore(), null, rules, p.shuffleOptions()));
            stats.count("管理员出卷");
            putOnline(created.id(), p, p.createdOn());
        }

        /** 上架；规则题量还不够（题还没加够）就第二天再试，直到结束日。 */
        void putOnline(long paperId, MockPaper p, LocalDate day) {
            try {
                touchAdmin();
                adminPapers.changeStatus(paperId, "ONLINE");
                stats.count("管理员上架试卷");
            } catch (BizException e) {
                stats.skip("上架试卷 " + p.title(), e);
                if (day.isBefore(plan.until())) {
                    once(workHours(day.plusDays(1)), () -> putOnline(paperId, p, day.plusDays(1)));
                }
                return;
            }
            if (Boolean.FALSE.equals(p.online())) {
                LocalDate off = day.plusDays(14 + rnd.nextInt(8));
                Instant at = off.isAfter(plan.until()) ? now().plusSeconds(600 + rnd.nextInt(3600)) : workHours(off);
                once(at.isAfter(until) ? until : at, () -> {
                    touchAdmin();
                    adminPapers.changeStatus(paperId, "OFFLINE");
                    stats.count("管理员下架试卷");
                });
            }
        }

        void handleFeedbackLater(FeedbackView f) {
            double r = rnd.nextDouble();
            if (r >= 0.75) {
                return; // 约 25% 一直没人处理
            }
            String status = r < 0.65 ? "RESOLVED" : "REJECTED";
            LocalDate day = today().plusDays(1 + rnd.nextInt(3));
            once(workHours(day), () -> {
                touchAdmin();
                adminSystem.handleFeedback(f.id(), new FeedbackHandle(status, pick(data.texts().adminReplies().get(status))));
                stats.count("管理员处理纠错（" + status + "）");
            });
        }

        void touchAdmin() {
            adminLastTouch = touch(adminId, adminLastTouch);
        }

        // ------------------------------------------------------------------ 学员

        void scheduleStudents(LocalDate day) {
            for (Student s : students) {
                if (s.disabled || day.isBefore(s.spec.registeredOn()) || day.isAfter(s.plan.lastDay())) {
                    continue;
                }
                DayPlan dp = s.plan.days().get(day);
                boolean registerToday = day.equals(s.spec.registeredOn());
                if (dp == null && !registerToday) {
                    continue;
                }
                schedule(s, day, dp, registerToday);
            }
        }

        void schedule(Student s, LocalDate day, DayPlan dp, boolean registerToday) {
            MockUser u = s.spec;
            List<Integer> hours = new ArrayList<>(u.hours());
            int sittings = dp != null && dp.questions() > 25 && hours.size() > 1 ? 2 : 1;
            List<Instant> starts = new ArrayList<>();
            for (int i = 0; i < sittings; i++) {
                starts.add(around(day, hours.remove(rnd.nextInt(hours.size()))));
            }
            starts.sort(null);
            List<Deque<Activity>> acts = new ArrayList<>();
            for (int i = 0; i < sittings; i++) {
                acts.add(new ArrayDeque<>());
            }
            if (registerToday) {
                acts.get(0).add(new Register(s));
                if (day.equals(plan.until()) && starts.get(0).isAfter(until)) {
                    starts.set(0, until.minusSeconds(60)); // 今天注册的人，注册时间不晚于“现在”
                }
            }
            if (dp != null) {
                acts.get(0).add(new SwitchTarget(s));
                int budget = dp.questions();
                if (rnd.nextDouble() < u.habits().daily()) {
                    acts.get(0).add(new Practice(s, "DAILY", DAILY_COUNT));
                    budget -= DAILY_COUNT;
                }
                int i = 0;
                for (int n = 0; budget > 0 && n < 4; n++) {
                    String mode = pickMode(u);
                    int size = "RANDOM".equals(mode) ? (budget >= 15 ? 20 : 10) : "WRONG".equals(mode) ? 10
                            : Math.min(budget, 20);
                    acts.get(i++ % sittings).add(new Practice(s, mode, size));
                    budget -= size;
                }
                if (dp.exam()) {
                    acts.get(sittings - 1).add(new Exam(s));
                }
            }
            for (int i = 0; i < sittings; i++) {
                if (!acts.get(i).isEmpty()) {
                    schedule(starts.get(i), new Sitting(s, acts.get(i)));
                }
            }
        }

        String pickMode(MockUser u) {
            double a = u.habits().random();
            double b = u.habits().sequential();
            double c = u.habits().wrongReview();
            double x = rnd.nextDouble() * (a + b + c);
            if (a + b + c <= 0 || x < a) {
                return "RANDOM";
            }
            return x < a + b ? "SEQUENTIAL" : "WRONG";
        }

        /** 学员的一段学习：依次做几个活动，活动之间隔几分钟。 */
        final class Sitting extends Event {
            final Student s;
            final Deque<Activity> todo;
            Activity current;

            Sitting(Student s, Deque<Activity> todo) {
                this.s = s;
                this.todo = todo;
            }

            @Override
            Instant run() {
                if (s.busy != null && s.busy != this) {
                    // 同一个人的上一段学习还没结束（两段时间挨着）：等它结束再开始，不会同时做两件事
                    return now().plusSeconds(600 + rnd.nextInt(600));
                }
                s.busy = this;
                Instant next = step();
                if (next == null) {
                    s.busy = null;
                }
                return next;
            }

            Instant step() {
                if (s.disabled || (s.id == null && !(todo.peek() instanceof Register) && current == null)) {
                    return null;
                }
                if (s.id != null) {
                    s.lastTouch = touch(s.id, s.lastTouch);
                }
                if (current == null) {
                    current = todo.poll();
                    if (current == null) {
                        return null;
                    }
                }
                long delay = current.step();
                if (delay >= 0) {
                    return now().plusSeconds(delay);
                }
                current = null;
                return todo.isEmpty() ? null : now().plusSeconds(60 + rnd.nextInt(240));
            }
        }

        /** 一个活动：每次 step 做一个动作，返回到下一个动作的秒数，做完返回 -1。 */
        abstract class Activity {
            abstract long step();
        }

        final class Register extends Activity {
            final Student s;
            int state;

            Register(Student s) {
                this.s = s;
            }

            @Override
            long step() {
                MockUser u = s.spec;
                if (state == 0) {
                    AuthResult r = userService.register(new RegisterRequest(u.username(), password, u.nickname()));
                    s.id = r.user().id();
                    s.lastTouch = now();
                    s.setTarget(paths.get(u.target()).getId());
                    userService.updateMe(s.id, new UpdateMeRequest(null, null, s.target));
                    if (u.wrongRemoveThreshold() != null && u.wrongRemoveThreshold() != UserService.DEFAULT_WRONG_REMOVE_THRESHOLD) {
                        userService.updateSettings(s.id, u.wrongRemoveThreshold());
                    }
                    stats.count("注册");
                    state = 1;
                    return Boolean.TRUE.equals(u.avatar()) ? 20 + rnd.nextInt(120) : -1;
                }
                String url = fileStorage.storeImage(new BytesMultipartFile("avatar.png", "image/png",
                        PngAvatar.generate(rnd)));
                userService.updateMe(s.id, new UpdateMeRequest(null, url, null));
                stats.count("上传头像");
                return -1;
            }
        }

        final class SwitchTarget extends Activity {
            final Student s;

            SwitchTarget(Student s) {
                this.s = s;
            }

            @Override
            long step() {
                MockData.SwitchTarget sw = s.spec.switchTarget();
                if (sw == null || s.switched || s.id == null || today().isBefore(sw.on())) {
                    return -1;
                }
                s.switched = true;
                s.setTarget(paths.get(sw.to()).getId());
                userService.updateMe(s.id, new UpdateMeRequest(null, null, s.target));
                stats.count("换目标考试");
                return 5 + rnd.nextInt(30);
            }
        }

        final class Practice extends Activity {
            final Student s;
            String mode;
            final int budget;
            boolean recite;
            SessionView view;
            List<SessionQuestion> todo;
            int next;
            int pendingSec;
            boolean finishing;

            Practice(Student s, String mode, int budget) {
                this.s = s;
                this.mode = mode;
                this.budget = budget;
            }

            @Override
            long step() {
                if (view == null) {
                    if (!start()) {
                        return -1;
                    }
                    todo = view.questions().stream().filter(q -> !q.submitted()).limit(budget).toList();
                    if (todo.isEmpty()) {
                        finish();
                        return -1;
                    }
                    pendingSec = thinkSeconds(todo.get(0).question().id());
                    return 3 + pendingSec;
                }
                if (next < todo.size()) {
                    SessionQuestion q = todo.get(next++);
                    if (recite) {
                        if (next % 3 == 0 || next == todo.size()) {
                            practiceService.saveProgress(s.id, view.id(), Math.min(view.questions().indexOf(q),
                                    view.questions().size() - 1));
                        }
                    } else {
                        submit(q);
                    }
                    if (next < todo.size()) {
                        pendingSec = thinkSeconds(todo.get(next).question().id());
                        return pendingSec;
                    }
                    return 5 + rnd.nextInt(30);
                }
                finish();
                return -1;
            }

            boolean start() {
                MockUser u = s.spec;
                boolean shuffle = rnd.nextDouble() < u.habits().shuffleOptions();
                recite = !"DAILY".equals(mode) && rnd.nextDouble() < u.habits().recite();
                StartRequest req = switch (mode) {
                    case "DAILY" -> new StartRequest("DAILY", null, null, null, null, false, null, null, null, null,
                            shuffle);
                    case "SEQUENTIAL" -> {
                        List<Long> chapters = chaptersOfRoot.getOrDefault(s.target, List.of());
                        if (chapters.isEmpty()) {
                            yield null;
                        }
                        Long chapter = chapters.get(s.sequentialIndex % chapters.size());
                        yield new StartRequest("SEQUENTIAL", List.of(chapter), null, null, null, recite, null, null,
                                null, null, shuffle);
                    }
                    case "WRONG" -> new StartRequest("WRONG", null, budget, null, null, recite, true, null, null,
                            null, shuffle);
                    default -> randomRequest(shuffle);
                };
                if (req == null) {
                    return false;
                }
                try {
                    view = practiceService.start(s.id, req).session();
                } catch (BizException e) {
                    if (retryAll(req, e)) {
                        stats.count("开练习（RANDOM，做完了没做过的题，改成全部）");
                        return true;
                    }
                    if ("WRONG".equals(mode)) {
                        mode = "RANDOM"; // 错题本还空着：换成随机练习
                        return startRandom(shuffle);
                    }
                    if ("SEQUENTIAL".equals(mode)) {
                        s.sequentialIndex++;
                    }
                    stats.skip("开练习（" + mode + "）", e);
                    return false;
                }
                stats.count("开练习（" + mode + (recite ? "，背题" : "") + (shuffle ? "，打乱" : "") + "）");
                return true;
            }

            boolean startRandom(boolean shuffle) {
                try {
                    view = practiceService.start(s.id, randomRequest(shuffle)).session();
                    stats.count("开练习（RANDOM" + (recite ? "，背题" : "") + (shuffle ? "，打乱" : "") + "）");
                    return true;
                } catch (BizException e) {
                    stats.skip("开练习（RANDOM）", e);
                    return false;
                }
            }

            /** 随机练习只抽没做过的题（UNDONE）却已经做完了：真实用户会去掉这个条件再抽一次。 */
            boolean retryAll(StartRequest req, BizException e) {
                if (!"RANDOM".equals(mode) || !"UNDONE".equals(req.scope())) {
                    return false;
                }
                try {
                    view = practiceService.start(s.id, new StartRequest("RANDOM", req.categoryIds(), req.count(), null,
                            null, req.reciteMode(), true, null, null, null, req.shuffleOptions())).session();
                    return true;
                } catch (BizException again) {
                    return false;
                }
            }

            StartRequest randomRequest(boolean shuffle) {
                List<Long> subjects = subjectsOfRoot.getOrDefault(s.target, List.of());
                List<Long> scope = subjects.isEmpty() || rnd.nextDouble() < 0.3 ? List.of(s.target)
                        : List.of(subjects.get(rnd.nextInt(subjects.size())));
                String range = rnd.nextDouble() < 0.3 ? "UNDONE" : null;
                return new StartRequest("RANDOM", scope, budget >= 15 ? 20 : 10, null, range, recite, true, null,
                        null, null, shuffle);
            }

            void submit(SessionQuestion sq) {
                Question q = question(sq.question().id());
                boolean correct = rnd.nextDouble() < chance(s, q);
                List<String> answer = answerFor(q, sq.optionMapping(), correct);
                SubmitResult r = practiceService.submit(s.id, view.id(), new SubmitRequest(q.getId(), answer, pendingSec));
                if (r.correct()) {
                    s.correctOnce.add(q.getId());
                }
                stats.count("练习提交");
                afterAnswer(s, q);
            }

            void finish() {
                if (finishing) {
                    return;
                }
                finishing = true;
                boolean allDone = !recite && view.questions().stream().filter(q -> !q.submitted()).count() <= next;
                if ("SEQUENTIAL".equals(mode) && allDone) {
                    s.sequentialIndex++; // 这一章做完了，下次往下一章
                }
                // 做完的练习会自动完成；没做完的一部分人点“结束”，其余的留着下次接着做
                if ("DAILY".equals(mode) || allDone || rnd.nextDouble() < 0.6) {
                    practiceService.finish(s.id, view.id());
                    stats.count("结束练习");
                }
            }
        }

        final class Exam extends Activity {
            final Student s;
            ExamSheet sheet;
            List<SheetQuestion> todo;
            int next;
            boolean willSubmit;

            Exam(Student s) {
                this.s = s;
            }

            @Override
            long step() {
                if (sheet == null) {
                    List<PaperItem> papers = examService.papers(s.id, null);
                    if (papers.isEmpty()) {
                        stats.count("想考试但没有上架的试卷");
                        return -1;
                    }
                    PaperItem p = papers.get(rnd.nextInt(papers.size()));
                    try {
                        sheet = examService.start(s.id, p.paperId()).sheet();
                    } catch (BizException e) {
                        stats.skip("开考 " + p.title(), e);
                        return -1;
                    }
                    stats.count("开考");
                    Set<Long> saved = new HashSet<>();
                    sheet.answers().forEach(a -> saved.add(a.questionId()));
                    todo = sheet.questions().stream().filter(q -> !saved.contains(q.questionId())).toList();
                    willSubmit = rnd.nextDouble() < 0.85;
                    return 20 + rnd.nextInt(60);
                }
                Instant deadline = sheet.deadlineAt();
                if (next < todo.size() && now().isBefore(deadline.minusSeconds(60))) {
                    SheetQuestion sq = todo.get(next++);
                    if (rnd.nextDouble() >= 0.04) { // 少数题空着不答
                        save(sq, 1);
                        if (rnd.nextDouble() < 0.08) {
                            save(sq, 2); // 改了一次答案
                        }
                    }
                    long per = Math.max(10, sheet.durationMin() * 60L * 8 / 10 / Math.max(1, sheet.questions().size()));
                    return Math.max(5, per / 2 + rnd.nextInt((int) Math.max(1, per)));
                }
                if (willSubmit) {
                    examService.submit(s.id, sheet.recordId());
                    stats.count("交卷");
                } else {
                    // 中途离开：到截止时间由定时结算记为超时
                    once(deadline.plusSeconds(15 + rnd.nextInt(90)), () -> {
                        int n = settlementJob.run();
                        stats.actions.merge("定时结算（超时）", (long) n, Long::sum);
                    });
                    stats.count("考试中途离开");
                }
                return -1;
            }

            void save(SheetQuestion sq, int version) {
                Question q = question(sq.questionId());
                boolean correct = rnd.nextDouble() < chance(s, q);
                List<String> answer = answerFor(q, sq.optionMapping(), correct);
                try {
                    examService.saveAnswer(s.id, sheet.recordId(), sq.questionId(),
                            new SaveAnswerRequest(answer, version, false));
                    stats.count("考试保存答案");
                } catch (BizException e) {
                    stats.skip("考试保存答案", e);
                }
            }
        }

        // ------------------------------------------------------------------ 作答

        Question question(long id) {
            return questionCache.computeIfAbsent(id, questionMapper::selectById);
        }

        /** 这一题答对的概率（docs/mock-data.md 第 2 节的 accuracy 说明）。 */
        double chance(Student s, Question q) {
            MockData.Accuracy a = s.spec.accuracy();
            LocalDate reg = s.spec.registeredOn();
            long span = Math.max(1, s.plan.lastDay().toEpochDay() - reg.toEpochDay());
            double t = Math.min(1, Math.max(0, (today().toEpochDay() - reg.toEpochDay()) / (double) span));
            double p = a.start() + (a.end() - a.start()) * t;
            String subject = subjectOfChapter.get(q.getCategoryId());
            if (a.subjects() != null && subject != null) {
                p += a.subjects().getOrDefault(subject, 0.0);
            }
            int d = q.getDifficulty() == null ? 3 : Math.max(1, Math.min(5, q.getDifficulty()));
            p += DIFFICULTY[d];
            if (s.correctOnce.contains(q.getId())) {
                p += 0.15;
            }
            return Math.max(0.05, Math.min(0.97, p));
        }

        /** 按显示标签给出答案；mapping 是“显示标签 → 原标签”，没打乱为 null。 */
        List<String> answerFor(Question q, Map<String, String> mapping, boolean correct) {
            AnswerKey key = AnswerKey.of(q);
            QuestionType type = key.type();
            if (type == QuestionType.FILL) {
                List<String> out = new ArrayList<>();
                List<List<String>> accepted = key.acceptedAnswers();
                int wrongBlank = correct ? -1 : rnd.nextInt(accepted.size());
                for (int i = 0; i < accepted.size(); i++) {
                    out.add(i == wrongBlank ? (rnd.nextBoolean() ? "不会" : accepted.get(i).get(0) + "s")
                            : accepted.get(i).get(0));
                }
                return out;
            }
            List<String> keys = type == QuestionType.JUDGE ? List.of("T", "F")
                    : assembler.optionItems(q).stream().map(OptionItem::key).toList();
            Map<String, String> toDisplay = new HashMap<>();
            for (String k : keys) {
                toDisplay.put(k, k);
            }
            if (mapping != null) {
                toDisplay.clear();
                mapping.forEach((display, original) -> toDisplay.put(original, display));
            }
            Set<String> right = new HashSet<>();
            key.correctAnswer().forEach(k -> right.add(toDisplay.getOrDefault(k, k)));
            List<String> displayKeys = keys.stream().map(k -> toDisplay.getOrDefault(k, k)).sorted().toList();
            if (correct) {
                return right.stream().sorted().toList();
            }
            List<String> others = displayKeys.stream().filter(k -> !right.contains(k)).toList();
            if (type != QuestionType.MULTIPLE) {
                return List.of(others.get(rnd.nextInt(others.size())));
            }
            List<String> wrong = new ArrayList<>(right);
            if (wrong.size() > 1 && (others.isEmpty() || rnd.nextBoolean())) {
                wrong.remove(rnd.nextInt(wrong.size()));
            } else if (!others.isEmpty()) {
                wrong.add(others.get(rnd.nextInt(others.size())));
            }
            return wrong.stream().sorted().toList();
        }

        /** 每一题做完顺手收藏、纠错。 */
        void afterAnswer(Student s, Question q) {
            MockUser u = s.spec;
            if (rnd.nextDouble() < u.habits().favorite()) {
                String note = rnd.nextBoolean() ? null : note(q);
                favoriteService.upsert(s.id, q.getId(), note);
                stats.count("收藏");
            }
            if (rnd.nextDouble() < u.habits().feedback()) {
                String type = FEEDBACK_TYPES[weighted(FEEDBACK_WEIGHTS)];
                String content = pick(data.texts().feedback().get(type))
                        .replace("{option}", option(q))
                        .replace("{chapter}", chapterName.getOrDefault(q.getCategoryId(), "这一章"));
                try {
                    FeedbackView f = questionService.feedback(s.id, q.getId(), new FeedbackRequest(type, content));
                    stats.count("纠错");
                    handleFeedbackLater(f);
                } catch (BizException e) {
                    stats.skip("纠错", e);
                }
            }
        }

        String note(Question q) {
            Map<String, List<String>> notes = data.texts().favoriteNotes();
            List<String> pool = notes.get(subjectOfChapter.get(q.getCategoryId()));
            return pick(pool == null || pool.isEmpty() || rnd.nextDouble() < 0.4 ? notes.get("*") : pool);
        }

        String option(Question q) {
            List<OptionItem> options = assembler.optionItems(q);
            if (options.isEmpty()) {
                return "答案";
            }
            return options.get(rnd.nextInt(options.size())).key();
        }

        int thinkSeconds(long questionId) {
            Question q = question(questionId);
            int d = q.getDifficulty() == null ? 3 : q.getDifficulty();
            int base = 15 + d * 12 + ("FILL".equals(q.getType()) ? 20 : 0);
            return base / 2 + rnd.nextInt(base + 1);
        }

        String pick(List<String> pool) {
            return pool.get(rnd.nextInt(pool.size()));
        }

        int weighted(double[] w) {
            double sum = 0;
            for (double x : w) {
                sum += x;
            }
            double x = rnd.nextDouble() * sum;
            for (int i = 0; i < w.length; i++) {
                x -= w[i];
                if (x < 0) {
                    return i;
                }
            }
            return w.length - 1;
        }

        /**
         * 最后活跃时间：真实使用时由鉴权过滤器每 5 分钟写一次，模拟器不走 HTTP，按同一规则补上。
         * 返回这次写入的时间（没写就原样返回）。
         */
        Instant touch(long userId, Instant last) {
            Instant now = now();
            if (last != null && !last.plus(ACTIVE_WRITE_INTERVAL).isBefore(now)) {
                return last;
            }
            LocalDateTime t = TimeUtil.toUtc(now);
            userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, userId)
                    .set(User::getLastActiveAt, t));
            return now;
        }

        /** 计划里的学员和运行时状态。 */
        final class Student {
            final UserPlan plan;
            final MockUser spec;
            Long id;
            Long target;
            Instant lastTouch;
            boolean switched;
            boolean disabled;
            /** 正在进行的那一段学习；一个人同一时间只做一段。 */
            Event busy;
            int sequentialIndex;
            final Set<Long> correctOnce = new HashSet<>();

            Student(UserPlan plan) {
                this.plan = plan;
                this.spec = plan.user();
            }

            void setTarget(long id) {
                if (target == null || target != id) {
                    sequentialIndex = 0;
                }
                target = id;
            }
        }
    }

    /** 队列里的一个事件。 */
    private abstract static class Event {
        Instant at;
        long order;

        /** 执行；返回下一次执行的时间，结束返回 null。 */
        abstract Instant run();
    }

    /** 给汇总用：把计数表格式化成一行一项。 */
    static String lines(Map<String, Long> m) {
        StringBuilder sb = new StringBuilder();
        new LinkedHashMap<>(m).forEach((k, v) -> sb.append("    ").append(k).append("：").append(v).append('\n'));
        return sb.toString();
    }
}
