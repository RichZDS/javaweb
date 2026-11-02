package com.richzds.quiz.api;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.beans.factory.annotation.Autowired;
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
}
