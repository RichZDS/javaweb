package com.richzds.quiz.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.test.context.TestPropertySource;

import com.richzds.quiz.entity.Category;
import com.richzds.quiz.entity.ExamPaper;
import com.richzds.quiz.entity.PaperQuestion;
import com.richzds.quiz.entity.Question;
import com.richzds.quiz.exam.ExamSettlementJob;
import com.richzds.quiz.mapper.ExamPaperMapper;
import com.richzds.quiz.mapper.PaperQuestionMapper;
import com.richzds.quiz.support.ApiTestBase;

/**
 * 并发场景：同一次练习的多道题同时提交（练习必须自动完成，每日一练要靠它写 completedAt）、
 * 同时收藏同一道题、同一道考试题的多个版本同时保存、同一场考试同时交卷多次。
 * 连接用可重复读隔离级别，和 MySQL 的默认值一致（H2 默认是读已提交，复现不了快照读漏数的问题）。
 */
@TestPropertySource(properties = "spring.datasource.hikari.transaction-isolation=TRANSACTION_REPEATABLE_READ")
class PracticeConcurrencyApiTest extends ApiTestBase {

    @Autowired
    ExamPaperMapper paperMapper;
    @Autowired
    PaperQuestionMapper paperQuestionMapper;
    @Autowired
    ExamSettlementJob settlementJob;
    @Autowired
    DataSource dataSource;

    static final int ROUNDS = 15;
    static final int QUESTIONS = 10;

    @Test
    void concurrentSubmissionsStillCompleteTheSession() throws Exception {
        Login u = register("conc1");
        Category[] t = tree("T");
        singles(t[2], QUESTIONS);
        ExecutorService pool = Executors.newFixedThreadPool(QUESTIONS);
        try {
            int stuck = 0;
            for (int round = 0; round < ROUNDS; round++) {
                JsonNode s = data(call(post("/api/practice/sessions", u, Map.of("mode", "RANDOM",
                        "categoryIds", List.of(t[2].getId()), "count", 10))));
                long sid = s.get("id").asLong();
                CountDownLatch go = new CountDownLatch(1);
                List<Future<Resp>> results = new ArrayList<>();
                for (JsonNode q : s.get("questions")) {
                    long qid = q.get("id").asLong();
                    results.add(pool.submit(() -> {
                        go.await();
                        return call(post("/api/practice/sessions/" + sid + "/answers", u,
                                Map.of("questionId", qid, "userAnswer", List.of("A"))));
                    }));
                }
                go.countDown();
                for (Future<Resp> f : results) {
                    assertThat(f.get(30, TimeUnit.SECONDS).status()).isEqualTo(200);
                }
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM answer_record WHERE source_id = ?", Long.class,
                        sid)).isEqualTo(QUESTIONS);
                String status = jdbc.queryForObject("SELECT status FROM practice_session WHERE id = ?", String.class, sid);
                if (!"COMPLETED".equals(status)) {
                    stuck++;
                }
            }
            assertThat(stuck).as(ROUNDS + " 轮里全部提交后仍是进行中的轮数").isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    /** 同一个人同时收藏同一道题：只留一条，不能 500。 */
    @Test
    void concurrentFirstFavoriteNeverFails() throws Exception {
        Login u = register("conc2");
        Category[] t = tree("T");
        List<Long> qs = singles(t[2], ROUNDS).stream().map(q -> q.getId()).toList();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            for (long qid : qs) {
                CountDownLatch go = new CountDownLatch(1);
                List<Future<Resp>> results = new ArrayList<>();
                for (int i = 0; i < 8; i++) {
                    String note = "笔记" + i;
                    results.add(pool.submit(() -> {
                        go.await();
                        return call(put("/api/favorites/" + qid, u, Map.of("note", note)));
                    }));
                }
                go.countDown();
                for (Future<Resp> f : results) {
                    assertThat(f.get(30, TimeUnit.SECONDS).status()).as("同时收藏").isIn(200, 409);
                }
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM favorite WHERE question_id = ?", Long.class, qid))
                        .isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** 同一道题的多个版本同时保存：最后存下的是最大版本；被拒的返回的已存版本不小于自己的版本。 */
    @Test
    void concurrentAnswerVersionsKeepTheHighest() throws Exception {
        Login u = register("conc3");
        Category[] t = tree("T");
        List<Question> qs = singles(t[2], 3);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        ExamPaper p = new ExamPaper().setTitle("并发卷")
                .setCategoryId(t[0].getId()).setMode("FIXED").setDurationMin(30).setTotalScore(30).setPassScore(10)
                .setStatus("ONLINE").setCreatedAt(now).setUpdatedAt(now);
        paperMapper.insert(p);
        for (int i = 0; i < qs.size(); i++) {
            paperQuestionMapper.insert(new PaperQuestion().setPaperId(p.getId())
                    .setQuestionId(qs.get(i).getId()).setScore(10).setSort(i));
        }
        long rid = data(call(post("/api/exams/" + p.getId() + "/start", u, null))).get("recordId").asLong();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            for (Question q : qs) {
                CountDownLatch go = new CountDownLatch(1);
                List<Future<Resp>> results = new ArrayList<>();
                for (int v = 1; v <= 8; v++) {
                    int version = v;
                    results.add(pool.submit(() -> {
                        go.await();
                        return call(put("/api/exam-records/" + rid + "/answers/" + q.getId(), u,
                                Map.of("userAnswer", List.of("A"), "version", version)));
                    }));
                }
                go.countDown();
                for (int v = 1; v <= 8; v++) {
                    JsonNode r = data(results.get(v - 1).get(30, TimeUnit.SECONDS));
                    if (r.get("accepted").asBoolean()) {
                        assertThat(r.get("version").asInt()).isEqualTo(v);
                    } else {
                        assertThat(r.get("version").asInt()).as("被拒时返回的已存版本").isGreaterThanOrEqualTo(v);
                    }
                }
                assertThat(jdbc.queryForObject("SELECT version FROM exam_answer WHERE record_id = ? AND question_id = ?",
                        Integer.class, rid, q.getId())).isEqualTo(8);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * BUG-QA-004：同一场考试同时交卷多次（连点交卷、自动交卷撞上手动交卷）。只结算一次，而且每个请求返回的
     * 成绩单完全一致、等于库里存的成绩；不能有请求拿着结算前的快照，返回“进行中、0 分、全错”。
     */
    @Test
    void concurrentExamSubmitsAllReturnTheStoredResult() throws Exception {
        Login u = register("conc4");
        Category[] t = tree("T");
        List<Question> qs = singles(t[2], QUESTIONS);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        ExamPaper p = new ExamPaper().setTitle("并发交卷")
                .setCategoryId(t[0].getId()).setMode("FIXED").setDurationMin(30).setTotalScore(QUESTIONS * 10)
                .setPassScore(10).setStatus("ONLINE").setCreatedAt(now).setUpdatedAt(now);
        paperMapper.insert(p);
        for (int i = 0; i < qs.size(); i++) {
            paperQuestionMapper.insert(new PaperQuestion().setPaperId(p.getId())
                    .setQuestionId(qs.get(i).getId()).setScore(10).setSort(i));
        }
        ExecutorService pool = Executors.newFixedThreadPool(6);
        int mismatched = 0;
        try {
            for (int round = 0; round < ROUNDS; round++) {
                long rid = data(call(post("/api/exams/" + p.getId() + "/start", u, null))).get("recordId").asLong();
                for (int i = 0; i < qs.size(); i++) {
                    // 一半答对（A），一半答错（B）
                    call(put("/api/exam-records/" + rid + "/answers/" + qs.get(i).getId(), u,
                            Map.of("userAnswer", List.of(i % 2 == 0 ? "A" : "B"), "version", 1)));
                }
                CountDownLatch go = new CountDownLatch(1);
                List<Future<Resp>> results = new ArrayList<>();
                for (int i = 0; i < 6; i++) {
                    results.add(pool.submit(() -> {
                        go.await();
                        return call(post("/api/exam-records/" + rid + "/submit", u, null));
                    }));
                }
                go.countDown();
                List<JsonNode> bodies = new ArrayList<>();
                for (Future<Resp> f : results) {
                    bodies.add(data(f.get(30, TimeUnit.SECONDS)));
                }
                int stored = jdbc.queryForObject("SELECT score FROM exam_record WHERE id = ?", Integer.class, rid);
                long storedCorrect = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM exam_answer WHERE record_id = ? AND is_correct = TRUE", Long.class, rid);
                assertThat(stored).isEqualTo(QUESTIONS / 2 * 10);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM answer_record WHERE source = 'EXAM' AND source_id = ?",
                        Long.class, rid)).as("只结算一次").isEqualTo(QUESTIONS);
                for (JsonNode b : bodies) {
                    if (!b.equals(bodies.get(0)) || !"FINISHED".equals(b.get("status").asText())
                            || b.get("score").asInt() != stored || b.get("correctCount").asLong() != storedCorrect) {
                        mismatched++;
                    }
                }
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(mismatched).as("和库里成绩不一致的交卷返回数（共 %d 轮，每轮 6 个并发交卷）", ROUNDS).isZero();
    }

    /**
     * 到了截止时间，自动交卷、打开记录和定时结算撞在一起：只结算一次（记为超时），
     * 交卷和查看记录返回的成绩单都和库里一致。
     */
    @Test
    void submitRacingTheTimeoutJobReturnsTheStoredResult() throws Exception {
        Login u = register("conc5");
        Category[] t = tree("T");
        List<Question> qs = singles(t[2], QUESTIONS);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        ExamPaper p = new ExamPaper().setTitle("到时交卷")
                .setCategoryId(t[0].getId()).setMode("FIXED").setDurationMin(30).setTotalScore(QUESTIONS * 10)
                .setPassScore(10).setStatus("ONLINE").setCreatedAt(now).setUpdatedAt(now);
        paperMapper.insert(p);
        for (int i = 0; i < qs.size(); i++) {
            paperQuestionMapper.insert(new PaperQuestion().setPaperId(p.getId())
                    .setQuestionId(qs.get(i).getId()).setScore(10).setSort(i));
        }
        ExecutorService pool = Executors.newFixedThreadPool(6);
        int mismatched = 0;
        try {
            for (int round = 0; round < ROUNDS; round++) {
                long rid = data(call(post("/api/exams/" + p.getId() + "/start", u, null))).get("recordId").asLong();
                for (int i = 0; i < qs.size(); i++) {
                    call(put("/api/exam-records/" + rid + "/answers/" + qs.get(i).getId(), u,
                            Map.of("userAnswer", List.of(i % 2 == 0 ? "A" : "B"), "version", 1)));
                }
                clock.advance(Duration.ofMinutes(31));
                // 时钟跳过 5 分钟后第一个请求会写 last_active_at；先让它写掉。否则 5 个并发请求都在鉴权时改这一行，
                // H2 可重复读会把“等锁期间行被改过”报成死锁（InnoDB 只是排队），测到的就不是结算本身了
                call(get("/api/me", u));
                CountDownLatch go = new CountDownLatch(1);
                Future<Integer> job = pool.submit(() -> {
                    go.await();
                    return settlementJob.run();
                });
                List<Future<Resp>> results = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    boolean submit = i % 2 == 0;
                    results.add(pool.submit(() -> {
                        go.await();
                        return submit ? call(post("/api/exam-records/" + rid + "/submit", u, null))
                                : call(get("/api/exam-records/" + rid, u));
                    }));
                }
                go.countDown();
                job.get(30, TimeUnit.SECONDS);
                List<JsonNode> bodies = new ArrayList<>();
                for (Future<Resp> f : results) {
                    bodies.add(data(f.get(30, TimeUnit.SECONDS)));
                }
                int stored = jdbc.queryForObject("SELECT score FROM exam_record WHERE id = ?", Integer.class, rid);
                assertThat(stored).isEqualTo(QUESTIONS / 2 * 10);
                assertThat(jdbc.queryForObject("SELECT finish_reason FROM exam_record WHERE id = ?", String.class, rid))
                        .isEqualTo("TIMEOUT");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM answer_record WHERE source = 'EXAM' AND source_id = ?",
                        Long.class, rid)).as("只结算一次").isEqualTo(QUESTIONS);
                for (JsonNode b : bodies) {
                    if (!b.equals(bodies.get(0)) || !"FINISHED".equals(b.get("status").asText())
                            || b.get("score").asInt() != stored) {
                        mismatched++;
                    }
                }
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(mismatched).as("和库里成绩不一致的返回数（共 %d 轮，每轮 3 个交卷 + 2 个查看）", ROUNDS).isZero();
    }

    // ------------------------------------------------------------------ 只在 MySQL 上跑：间隙锁死锁

    private boolean isMysql() {
        String db = jdbc.execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
        return db != null && db.toLowerCase(java.util.Locale.ROOT).contains("mysql");
    }

    private void assumeMysql() {
        assumeTrue(isMysql(), "只在 MySQL 上跑：H2 没有间隙锁，测不出这类死锁（CI 的 backend-mysql 会跑）");
    }

    private static Connection begin(DataSource ds) throws SQLException {
        Connection c = ds.getConnection();
        c.setAutoCommit(false);
        c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        return c;
    }

    private static int exec(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps.executeUpdate();
        }
    }

    private static final String SAVE_UPDATE = "UPDATE exam_answer SET user_answer = '[\"A\"]', version = 2 "
            + "WHERE record_id = ? AND question_id = ? AND version < 2";
    private static final String SAVE_INSERT = "INSERT INTO exam_answer (record_id, question_id, user_answer, version, "
            + "marked, received_at) VALUES (?, ?, '[\"A\"]', 1, FALSE, ?)";

    private void awaitLockWait() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.INNODB_TRX WHERE trx_state = 'LOCK WAIT'",
                Long.class) == 0) {
            assertThat(System.nanoTime()).as("等第一个 INSERT 卡在锁上超时").isLessThan(deadline);
            // INNODB_TRX 是缓存，0.1 秒内没人读过才刷新；轮询要比这慢，否则一直看到旧数据
            Thread.sleep(200);
        }
    }

    /**
     * 对照（确定时序，直接用两个连接）：两场新开的考试各存第一题答案，键落在索引末尾同一个间隙里。
     * 旧写法“先 UPDATE 没命中、再 INSERT”必然死锁（MySQL 1213），新写法“先 INSERT”两边都不用等。
     * 这条证明 CI 的 InnoDB 上旧写法真的会死锁，下一条的并发测试才有意义。
     */
    @Test
    void updateThenInsertDeadlocksOnMysqlButInsertFirstDoesNot() throws Exception {
        assumeMysql();
        long base = jdbc.queryForObject("SELECT COALESCE(MAX(record_id), 0) FROM exam_answer", Long.class) + 1_000_000;
        Timestamp now = Timestamp.valueOf(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection c1 = begin(dataSource); Connection c2 = begin(dataSource)) {
            assertThat(exec(c1, SAVE_UPDATE, base + 1, 1L)).isZero();
            assertThat(exec(c2, SAVE_UPDATE, base + 2, 1L)).isZero();
            Future<Integer> first = pool.submit(() -> exec(c1, SAVE_INSERT, base + 1, 1L, now));
            awaitLockWait();
            int deadlocks = 0;
            try {
                exec(c2, SAVE_INSERT, base + 2, 1L, now);
            } catch (SQLException e) {
                assertThat(e.getErrorCode()).as("c2：" + e.getMessage()).isEqualTo(1213);
                deadlocks++;
                c2.rollback();
            }
            try {
                first.get(30, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException e) {
                SQLException cause = (SQLException) e.getCause();
                assertThat(cause.getErrorCode()).as("c1：" + cause.getMessage()).isEqualTo(1213);
                deadlocks++;
            }
            assertThat(deadlocks).as("旧写法：两个第一次保存恰好有一个被判死锁").isEqualTo(1);
            c1.rollback();
            c2.rollback();
        } finally {
            pool.shutdownNow();
        }

        ExecutorService pool2 = Executors.newSingleThreadExecutor();
        try (Connection c1 = begin(dataSource); Connection c2 = begin(dataSource)) {
            assertThat(exec(c1, SAVE_INSERT, base + 3, 1L, now)).isEqualTo(1);
            Future<Integer> second = pool2.submit(() -> exec(c2, SAVE_INSERT, base + 4, 1L, now));
            assertThat(second.get(5, TimeUnit.SECONDS)).as("新写法：不用等对方").isEqualTo(1);
            c1.rollback();
            c2.rollback();
        } finally {
            pool2.shutdownNow();
        }
    }

    /**
     * 全班同时开考（BE-QA004-OK）：多个新用户各自开一场考试，同时第一次保存答案、同时交卷（交卷时第一次写错题本），
     * 再同时在练习里第一次答错。每个请求都必须成功，不能有被判死锁的 500。
     */
    @Test
    void manyStudentsFirstSavesAndFirstWrongAnswersAtOnceNeverDeadlock() throws Exception {
        assumeMysql();
        int students = 16;
        Category[] t = tree("Class");
        List<Question> examQs = singles(t[2], 5);
        Category practiceChapter = category(t[1], "练习章节", 1);
        List<Question> practiceQs = singles(practiceChapter, 10);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        ExamPaper p = new ExamPaper().setTitle("课堂模拟考")
                .setCategoryId(t[0].getId()).setMode("FIXED").setDurationMin(30).setTotalScore(50).setPassScore(10)
                .setStatus("ONLINE").setCreatedAt(now).setUpdatedAt(now);
        paperMapper.insert(p);
        for (int i = 0; i < examQs.size(); i++) {
            paperQuestionMapper.insert(new PaperQuestion().setPaperId(p.getId())
                    .setQuestionId(examQs.get(i).getId()).setScore(10).setSort(i));
        }
        ExecutorService pool = Executors.newFixedThreadPool(students);
        List<String> failures = new ArrayList<>();
        try {
            for (int round = 0; round < 3; round++) {
                List<Login> us = new ArrayList<>();
                List<Long> records = new ArrayList<>();
                List<Long> sessions = new ArrayList<>();
                for (int i = 0; i < students; i++) {
                    Login u = register("cls" + round + "_" + i);
                    us.add(u);
                    records.add(data(call(post("/api/exams/" + p.getId() + "/start", u, null))).get("recordId").asLong());
                    sessions.add(data(call(post("/api/practice/sessions", u, Map.of("mode", "RANDOM",
                            "categoryIds", List.of(practiceChapter.getId()), "count", 10)))).get("id").asLong());
                }
                failures.addAll(allAtOnce(pool, students, i -> {
                    List<Resp> out = new ArrayList<>();
                    for (Question q : examQs) {
                        out.add(call(put("/api/exam-records/" + records.get(i) + "/answers/" + q.getId(), us.get(i),
                                Map.of("userAnswer", List.of("B"), "version", 1))));
                    }
                    return out;
                }, "第一次保存答案"));
                failures.addAll(allAtOnce(pool, students, i -> List.of(
                        call(post("/api/exam-records/" + records.get(i) + "/submit", us.get(i), null))), "交卷"));
                failures.addAll(allAtOnce(pool, students, i -> List.of(
                        call(post("/api/practice/sessions/" + sessions.get(i) + "/answers", us.get(i),
                                Map.of("questionId", practiceQs.get(i % practiceQs.size()).getId(),
                                        "userAnswer", List.of("B"))))), "练习第一次答错"));
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(failures).as("失败的请求").isEmpty();
    }

    interface StudentCall {
        List<Resp> run(int student) throws Exception;
    }

    /** 让 n 个学生同时发请求，返回不是 2xx 的那些（附状态和返回体）。 */
    private List<String> allAtOnce(ExecutorService pool, int n, StudentCall call, String what) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        List<Future<List<Resp>>> results = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int student = i;
            results.add(pool.submit(() -> {
                go.await();
                return call.run(student);
            }));
        }
        go.countDown();
        List<String> failed = new ArrayList<>();
        for (Future<List<Resp>> f : results) {
            for (Resp r : f.get(60, TimeUnit.SECONDS)) {
                if (r.status() / 100 != 2) {
                    failed.add(what + "：" + r.status() + " " + r.body());
                }
            }
        }
        return failed;
    }
}
