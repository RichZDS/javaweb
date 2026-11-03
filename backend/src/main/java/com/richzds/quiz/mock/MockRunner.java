package com.richzds.quiz.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IntSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.richzds.quiz.common.RandomSource;
import com.richzds.quiz.common.TimeUtil;
import com.richzds.quiz.entity.Category;
import com.richzds.quiz.mapper.CategoryMapper;
import com.richzds.quiz.mock.MockData.MockUser;
import com.richzds.quiz.mock.MockPlanner.Plan;
import com.richzds.quiz.mock.MockPlanner.UserPlan;
import com.richzds.quiz.service.CategoryTree;

/**
 * {@code app.mock.enabled=true} 时，启动完成（管理员、示例题库都就绪）后跑一次演示数据模拟，打印汇总后退出。
 * {@code app.mock.dry-run=true} 只读数据、打印计划，不写库。见 docs/mock-data.md 第 6、7 节。
 */
@Component
@ConditionalOnProperty(name = "app.mock.enabled", havingValue = "true")
@Order(100)
public class MockRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MockRunner.class);
    /** 预计作答条数的合理范围（docs/mock-data.md 第 2 节）。 */
    static final long ANSWERS_MIN = 150_000;
    static final long ANSWERS_MAX = 250_000;
    private static final List<String> TABLES = List.of("app_user", "practice_session", "answer_record",
            "wrong_question", "favorite", "question_feedback", "exam_record", "exam_answer", "question", "exam_paper",
            "paper_rule");

    private final ResourceLoader resources;
    private final ObjectMapper objectMapper;
    private final CategoryMapper categoryMapper;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final RandomSource randomSource;
    private final MockSimulator simulator;
    private final ConfigurableApplicationContext context;
    private final String location;
    private final boolean dryRun;
    private final String untilText;
    private final boolean exitWhenDone;
    private final String password;

    public MockRunner(ResourceLoader resources, ObjectMapper objectMapper, CategoryMapper categoryMapper,
                      JdbcTemplate jdbc, Clock clock, RandomSource randomSource, MockSimulator simulator,
                      ConfigurableApplicationContext context,
                      @Value("${app.mock.location:classpath:mock/}") String location,
                      @Value("${app.mock.dry-run:false}") boolean dryRun,
                      @Value("${app.mock.until:}") String untilText,
                      @Value("${app.mock.exit:true}") boolean exitWhenDone,
                      @Value("${MOCK_USER_PASSWORD:}") String password) {
        this.resources = resources;
        this.objectMapper = objectMapper;
        this.categoryMapper = categoryMapper;
        this.jdbc = jdbc;
        this.clock = clock;
        this.randomSource = randomSource;
        this.simulator = simulator;
        this.context = context;
        this.location = location;
        this.dryRun = dryRun;
        this.untilText = untilText;
        this.exitWhenDone = exitWhenDone;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        int code = 0;
        try {
            execute();
        } catch (RuntimeException e) {
            log.error("演示数据模拟失败：{}", e.getMessage(), e);
            code = 1;
            if (!exitWhenDone) {
                throw e;
            }
        }
        if (exitWhenDone) {
            int exit = code;
            System.exit(SpringApplication.exit(context, () -> exit));
        }
    }

    /** 结束时间：没配置就是运行时的“现在”；配置了（测试、预演用）就用它。 */
    Instant until() {
        return untilText == null || untilText.isBlank() ? Instant.now() : Instant.parse(untilText.strip());
    }

    void execute() {
        if (!(clock instanceof MockClock mockClock)) {
            throw new IllegalStateException("模拟器需要可替换的时钟（app.mock.enabled=true 时由 ClockConfig 提供）");
        }
        Instant until = until();
        Instant realNow = Instant.now();
        if (until.isAfter(realNow)) {
            throw new IllegalStateException("app.mock.until 不能晚于现在（" + realNow + "），不能生成未来的记录");
        }
        LocalDate untilDay = TimeUtil.beijingDate(TimeUtil.toUtc(until));
        // 数据里的日期按“现在”校验；until 设得更早（试跑一段）时，之后的注册、加题等只是还没发生
        LocalDate today = TimeUtil.beijingDate(TimeUtil.toUtc(realNow));
        List<Category> all = categoryMapper.selectList(null);
        if (all.isEmpty()) {
            throw new IllegalStateException("库里没有分类：先导入示例题库（app.seed.enabled=true）再跑模拟");
        }
        Map<String, Category> paths = MockData.byPath(new CategoryTree(all), all);
        MockData data = MockData.load(resources, objectMapper, location);
        List<String> errors = data.validate(paths, today);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("演示数据有 " + errors.size() + " 处问题：\n  " + String.join("\n  ", errors));
        }
        Plan plan = MockPlanner.plan(data, untilDay);
        log.info("\n{}", describe(plan, data, paths));
        if (dryRun) {
            log.info("预演（app.mock.dry-run=true）：只打印计划，没有写库");
            return;
        }
        long adminId = checkEmptyDatabase();
        if (password == null || password.length() < 6 || password.length() > 32) {
            throw new IllegalStateException("请用环境变量 MOCK_USER_PASSWORD 给出学员密码（6–32 位）");
        }
        LocalDateTime baseline = TimeUtil.beijingDayStartUtc(plan.start().minusDays(14)).plusHours(2);
        redateBaseline(baseline);
        mockClock.set(TimeUtil.toInstant(baseline));
        randomSource.useSeed(data.seed() + 1);
        long t0 = System.nanoTime();
        MockSimulator.Stats stats = simulator.run(data, plan, mockClock, until, password, adminId);
        log.info("\n{}", summary(data, stats, (System.nanoTime() - t0) / 1_000_000_000));
    }

    /** 只能在“只有示例题库、只有管理员一个账号”的库上跑；返回管理员 id。 */
    long checkEmptyDatabase() {
        Long others = jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE role <> 'ADMIN'", Long.class);
        if (others != null && others > 0) {
            throw new IllegalStateException("库里已经有 " + others + " 个管理员以外的账号，演示数据只能灌进全新的库（防止重复）");
        }
        List<Long> admins = jdbc.queryForList("SELECT id FROM app_user WHERE role = 'ADMIN' ORDER BY id", Long.class);
        if (admins.isEmpty()) {
            throw new IllegalStateException("库里没有管理员账号：先配置 app.admin 启动一次");
        }
        return admins.get(0);
    }

    /** 示例题库和管理员账号的时间改到模拟开始之前，否则它们会比学员的记录还“新”。 */
    void redateBaseline(LocalDateTime t) {
        jdbc.update("UPDATE category SET created_at = ?", t);
        jdbc.update("UPDATE question SET created_at = ?, updated_at = ?", t, t);
        jdbc.update("UPDATE exam_paper SET created_at = ?, updated_at = ?", t, t);
        jdbc.update("UPDATE app_user SET created_at = ?, updated_at = ?, last_active_at = NULL WHERE role = 'ADMIN'", t, t);
    }

    // ------------------------------------------------------------------ 打印

    String describe(Plan plan, MockData data, Map<String, Category> paths) {
        StringBuilder sb = new StringBuilder("===== 演示数据计划 =====\n");
        sb.append("日期：").append(plan.start()).append(" 至 ").append(plan.until()).append("（北京时间），种子 ")
                .append(data.seed()).append('\n');
        sb.append("学员：").append(plan.users().size()).append(" 人；按目标考试：")
                .append(count(plan.users(), u -> u.user().target())).append("；按画像：")
                .append(count(plan.users(), u -> u.user().persona())).append('\n');
        long switches = plan.users().stream().filter(u -> u.user().switchTarget() != null).count();
        long disabled = plan.users().stream().filter(u -> Boolean.TRUE.equals(u.user().disabled())).count();
        long avatars = plan.users().stream().filter(u -> Boolean.TRUE.equals(u.user().avatar())).count();
        sb.append("换目标 ").append(switches).append(" 人，禁用 ").append(disabled).append(" 人，上传头像 ")
                .append(avatars).append(" 人\n");
        Map<LocalDate, Integer> active = plan.activeByDay();
        IntSummaryStatistics daily = active.values().stream().mapToInt(Integer::intValue).summaryStatistics();
        sb.append("每天学习人数：最少 ").append(daily.getMin()).append("，平均 ")
                .append(String.format("%.1f", daily.getAverage())).append("，最多 ").append(daily.getMax()).append('\n');
        Map<YearMonth, double[]> months = new TreeMap<>();
        active.forEach((d, n) -> {
            double[] m = months.computeIfAbsent(YearMonth.from(d), k -> new double[2]);
            m[0] += n;
            m[1]++;
        });
        sb.append("按月平均每天学习人数：");
        months.forEach((m, v) -> sb.append(m).append(' ').append(String.format("%.1f", v[0] / v[1])).append("  "));
        sb.append('\n');
        double recite = plan.users().stream().mapToDouble(u -> u.user().habits().recite()).average().orElse(0);
        long planned = plan.plannedQuestions();
        long practice = Math.round(planned * (1 - recite));
        long exams = plan.examDays();
        int examSize = averagePaperSize(data, paths);
        long examAnswers = exams * examSize;
        long total = practice + examAnswers;
        sb.append("计划练习题量 ").append(planned).append("（扣掉背题约 ").append(practice).append(" 条作答），考试 ")
                .append(exams).append(" 场 × 约 ").append(examSize).append(" 题 = ").append(examAnswers).append(" 条\n");
        sb.append("预计作答记录合计：约 ").append(total).append(" 条");
        if (total < ANSWERS_MIN || total > ANSWERS_MAX) {
            sb.append("（不在 ").append(ANSWERS_MIN).append("–").append(ANSWERS_MAX)
                    .append(" 之间，请调 activeDaysPerWeek、questionsPerDay）");
        }
        sb.append('\n').append("后台：").append(data.questions().size()).append(" 道新题，").append(data.papers().size())
                .append(" 份新试卷");
        return sb.toString();
    }

    /** 每场考试的题数估算：示例题库里已有试卷和 papers.json 里规则卷的平均题数。 */
    int averagePaperSize(MockData data, Map<String, Category> paths) {
        List<Integer> sizes = new ArrayList<>(jdbc.queryForList(
                "SELECT COALESCE((SELECT SUM(r.count) FROM paper_rule r WHERE r.paper_id = p.id), "
                        + "(SELECT COUNT(*) FROM paper_question q WHERE q.paper_id = p.id)) FROM exam_paper p",
                Integer.class));
        data.papers().forEach(p -> sizes.add(p.rules().stream().mapToInt(MockData.MockRule::count).sum()));
        return (int) Math.round(sizes.stream().mapToInt(Integer::intValue).average().orElse(30));
    }

    private static <K> Map<K, Long> count(List<UserPlan> users, java.util.function.Function<UserPlan, K> key) {
        return users.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.counting()));
    }

    String summary(MockData data, MockSimulator.Stats stats, long seconds) {
        StringBuilder sb = new StringBuilder("===== 演示数据汇总 =====\n");
        sb.append("用时 ").append(seconds).append(" 秒\n各表行数：");
        for (String t : TABLES) {
            sb.append(t).append(' ').append(jdbc.queryForObject("SELECT COUNT(*) FROM " + t, Long.class)).append("  ");
        }
        sb.append('\n');
        Map<String, Object> answers = jdbc.queryForMap("SELECT COUNT(*) AS total, "
                + "SUM(CASE WHEN source = 'PRACTICE' THEN 1 ELSE 0 END) AS practice, "
                + "SUM(CASE WHEN source = 'EXAM' THEN 1 ELSE 0 END) AS exam, "
                + "MIN(graded_at) AS first, MAX(graded_at) AS last FROM answer_record");
        sb.append("作答记录 ").append(answers.get("total")).append(" 条（练习 ").append(answers.get("practice"))
                .append("，考试 ").append(answers.get("exam")).append("），时间 ").append(answers.get("first"))
                .append(" 至 ").append(answers.get("last")).append("（UTC）\n");
        sb.append("每个考试的学员数（按当前目标）：");
        jdbc.queryForList("SELECT c.name AS name, COUNT(*) AS n FROM app_user u JOIN category c ON c.id = u.target_category_id "
                + "WHERE u.role <> 'ADMIN' GROUP BY c.name ORDER BY n DESC")
                .forEach(r -> sb.append(r.get("name")).append(' ').append(r.get("n")).append("  "));
        sb.append('\n').append("按画像的平均正确率：");
        Map<String, String> persona = new HashMap<>();
        for (MockUser u : data.users().users()) {
            persona.put(u.username(), u.persona());
        }
        Map<String, long[]> byPersona = new TreeMap<>();
        jdbc.queryForList("SELECT u.username AS username, COUNT(*) AS total, "
                + "SUM(CASE WHEN a.is_correct THEN 1 ELSE 0 END) AS correct "
                + "FROM answer_record a JOIN app_user u ON u.id = a.user_id WHERE a.answered = TRUE GROUP BY u.username")
                .forEach(r -> {
                    long[] v = byPersona.computeIfAbsent(persona.getOrDefault((String) r.get("username"), "?"),
                            k -> new long[2]);
                    v[0] += ((Number) r.get("total")).longValue();
                    v[1] += ((Number) r.get("correct")).longValue();
                });
        byPersona.forEach((p, v) -> sb.append(p).append(' ')
                .append(String.format("%.1f%%", v[0] == 0 ? 0 : 100.0 * v[1] / v[0])).append("  "));
        sb.append('\n').append("动作计数：\n").append(MockSimulator.lines(stats.actions()));
        if (!stats.skipped().isEmpty()) {
            sb.append("跳过（业务规则不允许，正常现象）：\n").append(MockSimulator.lines(stats.skipped()));
        }
        sb.append("结束时间之后、没执行的事件：").append(stats.droppedAfterUntil());
        return sb.toString();
    }
}
