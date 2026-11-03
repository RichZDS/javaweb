package com.richzds.quiz.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.richzds.quiz.common.TimeUtil;
import com.richzds.quiz.dto.StatsDtos.Overview;
import com.richzds.quiz.service.StatsService;

/**
 * H2 上的小样本：6 个学员（其中一个每天两段学习时间挨着、题量大，专门测时段不重叠）、2026-07-01 到 07-11（北京时间），启动时跑完整个模拟（docs/mock-data.md 第 6 节），
 * 然后只读核对：没有未来时间、没有早于注册的记录、统计接口和表里聚合一致、错题本和作答记录一致、
 * 考试分数等于每题得分之和、打卡天数合理、weekdayWeights 决定学习日、禁用和换目标、管理员的时间线。
 * 用单独的内存库，和其他测试类的库互不影响（模拟器只肯在只有管理员的库上跑）。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:quiz_mock_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        // 数据源四项一起钉死：CI 的 MySQL 任务用环境变量 SPRING_DATASOURCE_* 指向 MySQL，只改 url 会让 H2 地址配上 MySQL 驱动
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.seed.enabled=true",
        "app.seed.location=classpath:seed/",
        "app.mock.enabled=true",
        "app.mock.exit=false",
        "app.mock.location=classpath:mock-test/",
        "app.mock.until=" + MockSimulatorTest.UNTIL_TEXT,
        "app.upload.dir=./target/mock-test-uploads",
        "MOCK_USER_PASSWORD=mock-pass-123"})
@ActiveProfiles("test")
class MockSimulatorTest {

    static final String UNTIL_TEXT = "2026-07-11T12:00:00Z"; // 北京时间 07-11 20:00
    static final LocalDateTime UNTIL = LocalDateTime.of(2026, 7, 11, 12, 0);

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StatsService statsService;
    @Autowired
    MockRunner runner;
    @Autowired
    Clock clock;
    @Value("${app.upload.dir}")
    String uploadDir;

    private long userId(String username) {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username = ?", Long.class, username);
    }

    private long rootId(String name) {
        return jdbc.queryForObject("SELECT id FROM category WHERE name = ? AND level = 1", Long.class, name);
    }

    private static LocalDate beijing(LocalDateTime utc) {
        return TimeUtil.beijingDate(utc);
    }

    @Test
    void everyoneRegisteredWithProfileSettings() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE role = 'USER'", Long.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT wrong_remove_threshold FROM app_user WHERE username = 'lin_steady'",
                Integer.class)).isEqualTo(2);
        for (String u : List.of("lin_steady", "zhao_drop", "sun_new")) {
            String url = jdbc.queryForObject("SELECT avatar_url FROM app_user WHERE username = ?", String.class, u);
            assertThat(url).as(u + " 走真实上传接口传了头像").endsWith(".png");
            Path file = Path.of(uploadDir).resolve(url.substring(url.indexOf("/uploads/") + "/uploads/".length()));
            assertThat(Files.exists(file)).as(file.toString()).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT avatar_url FROM app_user WHERE username = 'wu_weekend'", String.class))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT created_at FROM app_user WHERE username = 'sun_new'", LocalDateTime.class))
                .as("注册日是北京时间 07-09").matches(t -> beijing(t).equals(LocalDate.of(2026, 7, 9)));
        assertThat(clock.instant()).as("结束时时钟停在结束时间").isEqualTo(Instant.parse(UNTIL_TEXT));
    }

    @Test
    void noRecordInTheFutureOrBeforeRegistration() {
        Map<String, List<String>> columns = Map.ofEntries(
                Map.entry("app_user", List.of("created_at", "updated_at", "last_active_at")),
                Map.entry("practice_session", List.of("started_at", "completed_at", "updated_at")),
                Map.entry("answer_record", List.of("graded_at")),
                Map.entry("wrong_question", List.of("created_at", "last_wrong_at")),
                Map.entry("favorite", List.of("created_at", "updated_at")),
                Map.entry("question_feedback", List.of("created_at", "handled_at")),
                Map.entry("exam_record", List.of("started_at", "submitted_at")),
                Map.entry("exam_answer", List.of("received_at")),
                Map.entry("question", List.of("created_at", "updated_at")),
                Map.entry("exam_paper", List.of("created_at", "updated_at")));
        columns.forEach((table, cols) -> cols.forEach(col -> {
            LocalDateTime max = jdbc.queryForObject("SELECT MAX(" + col + ") FROM " + table, LocalDateTime.class);
            assertThat(max == null || !max.isAfter(UNTIL)).as(table + "." + col + " 不晚于结束时间：" + max).isTrue();
        }));
        Map<String, String> own = Map.of("practice_session", "started_at", "answer_record", "graded_at",
                "wrong_question", "created_at", "favorite", "created_at", "question_feedback", "created_at",
                "exam_record", "started_at");
        own.forEach((table, col) -> assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " t "
                + "JOIN app_user u ON u.id = t.user_id WHERE t." + col + " < u.created_at", Long.class))
                .as(table + " 没有早于注册的记录").isZero());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM answer_record", Long.class)).isGreaterThan(300);
        LocalDateTime first = jdbc.queryForObject("SELECT MIN(created_at) FROM app_user WHERE role = 'USER'",
                LocalDateTime.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM question WHERE created_at < ?", Long.class, first))
                .as("示例题库（396 道）的时间改到了模拟开始之前").isGreaterThanOrEqualTo(396);
    }

    @Test
    void statsEndpointsMatchTheTables() {
        for (String u : List.of("lin_steady", "wu_weekend", "zhao_drop", "qian_switch", "sun_new")) {
            long id = userId(u);
            for (String exam : List.of("计算机考研 408", "Java 程序设计", "数据库原理与 MySQL")) {
                long root = rootId(exam);
                Overview o = statsService.overview(id, root);
                Map<String, Object> row = jdbc.queryForMap("SELECT COUNT(*) AS total, "
                        + "COALESCE(SUM(CASE WHEN is_correct = TRUE THEN 1 ELSE 0 END), 0) AS correct, "
                        + "COUNT(DISTINCT CAST(TIMESTAMPADD(HOUR, 8, graded_at) AS DATE)) AS days "
                        + "FROM answer_record WHERE user_id = ? AND answered = TRUE AND root_category_id = ?", id, root);
                assertThat(o.answerCount()).as(u + " / " + exam).isEqualTo(((Number) row.get("total")).longValue());
                assertThat(o.correctCount()).isEqualTo(((Number) row.get("correct")).longValue());
                assertThat(o.studyDays()).isEqualTo(((Number) row.get("days")).longValue());
                assertThat(o.examCount()).isEqualTo(jdbc.queryForObject("SELECT COUNT(*) FROM exam_record r "
                        + "JOIN exam_paper p ON p.id = r.paper_id WHERE r.user_id = ? AND r.status = 'FINISHED' "
                        + "AND p.category_id = ?", Long.class, id, root));
            }
        }
    }

    @Test
    void wrongBookMatchesAnswerRecords() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT w.wrong_count AS wc, (SELECT COUNT(*) FROM "
                + "answer_record a WHERE a.user_id = w.user_id AND a.question_id = w.question_id AND a.answered = TRUE "
                + "AND a.is_correct = FALSE) AS wrongs FROM wrong_question w");
        assertThat(rows).isNotEmpty();
        rows.forEach(r -> assertThat(((Number) r.get("wc")).longValue()).as("错题次数等于答错的作答条数")
                .isEqualTo(((Number) r.get("wrongs")).longValue()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT user_id, question_id FROM answer_record "
                + "WHERE answered = TRUE AND is_correct = FALSE GROUP BY user_id, question_id) x WHERE NOT EXISTS "
                + "(SELECT 1 FROM wrong_question w WHERE w.user_id = x.user_id AND w.question_id = x.question_id)",
                Long.class)).as("答错过的题都在错题本里").isZero();
    }

    @Test
    void examScoreIsTheSumOfItsAnswers() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT r.score AS score, (SELECT COALESCE(SUM(a.score), 0) "
                + "FROM exam_answer a WHERE a.record_id = r.id) AS items FROM exam_record r WHERE r.status = 'FINISHED'");
        assertThat(rows).as("小样本里有结算过的考试").isNotEmpty();
        rows.forEach(r -> assertThat(((Number) r.get("score")).intValue())
                .isEqualTo(((Number) r.get("items")).intValue()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exam_record r WHERE r.status = 'FINISHED' AND "
                + "r.submitted_at > r.deadline_at", Long.class)).as("交卷时间不晚于截止时间").isZero();
    }

    @Test
    void streaksAreConsistentWithDailyPractice() {
        LocalDate today = TimeUtil.todayBeijing(clock);
        for (String u : List.of("lin_steady", "wu_weekend", "qian_switch", "sun_new")) {
            long id = userId(u);
            long root = jdbc.queryForObject("SELECT target_category_id FROM app_user WHERE id = ?", Long.class, id);
            Overview o = statsService.overview(id, root);
            Set<LocalDate> done = new HashSet<>(jdbc.queryForList("SELECT daily_date FROM practice_session WHERE "
                    + "user_id = ? AND mode = 'DAILY' AND completed_at IS NOT NULL AND root_category_id = ?",
                    LocalDate.class, id, root));
            LocalDate d = done.contains(today) ? today : today.minusDays(1);
            int streak = 0;
            while (done.contains(d)) {
                streak++;
                d = d.minusDays(1);
            }
            LocalDateTime registered = jdbc.queryForObject("SELECT created_at FROM app_user WHERE id = ?",
                    LocalDateTime.class, id);
            assertThat(o.streakDays()).as(u).isEqualTo(streak);
            assertThat(o.streakDays()).isLessThanOrEqualTo((int) o.studyDays())
                    .isLessThanOrEqualTo((int) (today.toEpochDay() - beijing(registered).toEpochDay() + 1));
        }
        long lin = userId("lin_steady");
        assertThat(statsService.overview(lin, null).streakDays()).as("每天学、每日一练概率 0.9 的人有连续打卡")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void aStudentDoesOneThingAtATime() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT a.source_id AS s, a.graded_at AS t, "
                + "LAG(a.source_id) OVER (PARTITION BY a.user_id ORDER BY a.graded_at, a.id) AS prev_s, "
                + "LAG(a.graded_at) OVER (PARTITION BY a.user_id ORDER BY a.graded_at, a.id) AS prev_t, "
                + "LEAD(a.source_id) OVER (PARTITION BY a.user_id ORDER BY a.graded_at, a.id) AS next_s "
                + "FROM answer_record a WHERE a.source = 'PRACTICE') z WHERE z.prev_s <> z.s AND z.next_s = z.prev_s "
                + "AND TIMESTAMPDIFF(MINUTE, z.prev_t, z.t) < 10", Long.class))
                .as("几分钟内在两套练习之间来回切换（两段学习重叠）").isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exam_record r JOIN answer_record a ON a.user_id = r.user_id "
                + "AND a.source = 'PRACTICE' AND a.graded_at > r.started_at "
                + "AND a.graded_at < COALESCE(r.submitted_at, r.deadline_at)", Long.class)).as("考试期间没有练习").isZero();
    }

    @Test
    void weekdayWeightsDecideStudyDays() {
        long wu = userId("wu_weekend");
        List<LocalDateTime> starts = jdbc.queryForList("SELECT started_at FROM practice_session WHERE user_id = ?",
                LocalDateTime.class, wu);
        assertThat(starts).as("周六日都学（7/4、7/5、7/11）").isNotEmpty();
        starts.forEach(t -> assertThat(beijing(t).getDayOfWeek()).as(t.toString())
                .isIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));
        assertThat(beijing(jdbc.queryForObject("SELECT created_at FROM app_user WHERE id = ?", LocalDateTime.class, wu)))
                .as("周三注册，但只在周末学习").isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void churnedUserIsDisabledAfterTheLastActiveDay() {
        long zhao = userId("zhao_drop");
        assertThat(jdbc.queryForObject("SELECT status FROM app_user WHERE id = ?", String.class, zhao))
                .isEqualTo("DISABLED");
        LocalDateTime lastDayEnd = TimeUtil.beijingDayStartUtc(LocalDate.of(2026, 7, 6));
        assertThat(jdbc.queryForObject("SELECT MAX(graded_at) FROM answer_record WHERE user_id = ?",
                LocalDateTime.class, zhao)).isBefore(lastDayEnd);
        assertThat(jdbc.queryForObject("SELECT updated_at FROM app_user WHERE id = ?", LocalDateTime.class, zhao))
                .as("禁用发生在最后活跃日之后").isAfterOrEqualTo(lastDayEnd);
    }

    @Test
    void targetSwitchMovesLaterPracticeToTheNewExam() {
        long qian = userId("qian_switch");
        long java = rootId("Java 程序设计");
        assertThat(jdbc.queryForObject("SELECT target_category_id FROM app_user WHERE id = ?", Long.class, qian))
                .isEqualTo(java);
        LocalDateTime switchDay = TimeUtil.beijingDayStartUtc(LocalDate.of(2026, 7, 6));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM practice_session WHERE user_id = ? AND mode = 'DAILY' "
                + "AND started_at < ? AND root_category_id <> ?", Long.class, qian, switchDay, java))
                .as("换之前的每日一练是 408 的").isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM practice_session WHERE user_id = ? AND mode = 'DAILY' "
                + "AND started_at >= ? AND root_category_id <> ?", Long.class, qian, switchDay, java))
                .as("换之后的每日一练都是 Java 的").isZero();
    }

    @Test
    void adminTimelineFollowsTheData() {
        LocalDateTime created = jdbc.queryForObject("SELECT created_at FROM question WHERE stem LIKE 'ArrayList 默认%'",
                LocalDateTime.class);
        assertThat(beijing(created)).isEqualTo(LocalDate.of(2026, 7, 2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM answer_record a JOIN question q ON q.id = a.question_id "
                + "WHERE a.graded_at < q.created_at", Long.class)).as("题发布之前没人做过").isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM question WHERE stem LIKE 'WHERE x = NULL%'", String.class))
                .isEqualTo("DRAFT");
        List<String> archived = jdbc.queryForList("SELECT status FROM question WHERE stem LIKE '栈的特点%'", String.class);
        assertThat(archived.isEmpty() || archived.get(0).equals("ARCHIVED")).as("旧题归档（没人做过就真删）").isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM exam_paper WHERE title = '408 小测（一）'", String.class))
                .isEqualTo("ONLINE");
        assertThat(jdbc.queryForObject("SELECT status FROM exam_paper WHERE title = 'Java 旧卷'", String.class))
                .isEqualTo("OFFLINE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM question_feedback", Long.class)).isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM question_feedback WHERE status <> 'PENDING' AND "
                + "(handled_at IS NULL OR handled_at <= created_at)", Long.class)).as("处理时间晚于提交时间").isZero();
        assertThat(jdbc.queryForObject("SELECT last_active_at FROM app_user WHERE role = 'ADMIN'", LocalDateTime.class))
                .as("管理员的最后活跃时间按模拟时间补上").isNotNull().isBefore(UNTIL.plusSeconds(1));
    }

    @Test
    void refusesToRunAgainOnAPopulatedDatabase() {
        assertThatThrownBy(() -> runner.checkEmptyDatabase()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("管理员以外的账号");
    }

    /**
     * 生成结果的指纹（作答、练习、考试、错题本的关键列），打印到日志。同一份数据、同一个种子、同一个结束时间，
     * 两次运行的指纹应当一样（人工比对两次运行的日志；不写死成常量，免得题库或规则一改就要跟着改）。
     */
    @Test
    void printFingerprint() throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        for (String sql : List.of(
                "SELECT user_id, question_id, source, source_id, user_answer, is_correct, graded_at, time_spent_sec "
                        + "FROM answer_record ORDER BY id",
                "SELECT user_id, mode, question_ids, option_orders, status, started_at, completed_at "
                        + "FROM practice_session ORDER BY id",
                "SELECT user_id, paper_id, status, score, started_at, submitted_at FROM exam_record ORDER BY id",
                "SELECT user_id, question_id, wrong_count, correct_streak, removed FROM wrong_question ORDER BY id")) {
            jdbc.query(sql, rs -> {
                int n = rs.getMetaData().getColumnCount();
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= n; i++) {
                    row.append(rs.getString(i)).append('|');
                }
                md.update(row.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            });
        }
        String hex = java.util.HexFormat.of().formatHex(md.digest());
        org.slf4j.LoggerFactory.getLogger(MockSimulatorTest.class).info("MOCK-FINGERPRINT {}", hex);
        assertThat(hex).hasSize(64);
    }
}
