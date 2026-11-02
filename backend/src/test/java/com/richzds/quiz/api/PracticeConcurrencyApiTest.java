package com.richzds.quiz.api;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.richzds.quiz.mapper.ExamPaperMapper;
import com.richzds.quiz.mapper.PaperQuestionMapper;
import com.richzds.quiz.support.ApiTestBase;

/**
 * 并发场景：同一次练习的多道题同时提交（练习必须自动完成，每日一练要靠它写 completedAt）、
 * 同时收藏同一道题、同一道考试题的多个版本同时保存。
 * 连接用可重复读隔离级别，和 MySQL 的默认值一致（H2 默认是读已提交，复现不了快照读漏数的问题）。
 */
@TestPropertySource(properties = "spring.datasource.hikari.transaction-isolation=TRANSACTION_REPEATABLE_READ")
class PracticeConcurrencyApiTest extends ApiTestBase {

    @Autowired
    ExamPaperMapper paperMapper;
    @Autowired
    PaperQuestionMapper paperQuestionMapper;

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
}
