package com.richzds.quiz.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.richzds.quiz.entity.Category;
import com.richzds.quiz.entity.ExamPaper;
import com.richzds.quiz.entity.PaperQuestion;
import com.richzds.quiz.entity.Question;
import com.richzds.quiz.mapper.ExamPaperMapper;
import com.richzds.quiz.mapper.PaperQuestionMapper;
import com.richzds.quiz.support.ApiTestBase;

/** 统计口径：北京时间跨日、连续打卡、薄弱点、章节、考试成绩、周排行榜。 */
class StatsApiTest extends ApiTestBase {

    @Autowired
    ExamPaperMapper paperMapper;
    @Autowired
    PaperQuestionMapper paperQuestionMapper;

    /** 在当前时钟时刻用一次顺序练习正式提交一道题。 */
    private void answer(Login u, Category chapter, Question q, String choice) throws Exception {
        JsonNode s = data(call(post("/api/practice/sessions", u,
                Map.of("mode", "SEQUENTIAL", "categoryIds", List.of(chapter.getId())))));
        long sid = s.get("id").asLong();
        data(call(post("/api/practice/sessions/" + sid + "/answers", u,
                Map.of("questionId", q.getId(), "userAnswer", List.of(choice)))));
        call(post("/api/practice/sessions/" + sid + "/finish", u, null));
    }

    private void answerAll(Login u, Category chapter, List<Question> qs, String choice) throws Exception {
        for (Question q : qs) {
            answer(u, chapter, q, choice);
        }
    }

    /** 在当前时钟这一天做完（或只做一部分）每日一练。 */
    private void daily(Login u, int submit) throws Exception {
        JsonNode d = data(call(post("/api/practice/sessions", u, Map.of("mode", "DAILY"))));
        List<Long> ids = ids(d.get("questions"), "id");
        for (Long id : ids.subList(0, Math.min(submit, ids.size()))) {
            call(post("/api/practice/sessions/" + d.get("id").asLong() + "/answers", u,
                    Map.of("questionId", id, "userAnswer", List.of("A"))));
        }
    }

    private JsonNode stats(Login u, String path) throws Exception {
        return data(call(get("/api/stats/" + path, u)));
    }

    @Test
    void daysAreSplitAtBeijingMidnight() throws Exception {
        Login u = register("stat1");
        Category[] t = tree("T");
        List<Question> qs = singles(t[2], 2);
        setTarget(u, t[0]);

        clock.set(Instant.parse("2026-11-02T15:59:00Z")); // 北京时间 11-02 23:59
        answer(u, t[2], qs.get(0), "A");
        clock.set(Instant.parse("2026-11-02T16:00:00Z")); // 北京时间 11-03 00:00
        answer(u, t[2], qs.get(1), "B");
        clock.set(Instant.parse("2026-11-03T02:00:00Z"));

        JsonNode daily = stats(u, "daily?days=3");
        assertThat(daily).hasSize(3);
        assertThat(daily.findValuesAsText("date")).containsExactly("2026-11-01", "2026-11-02", "2026-11-03");
        assertThat(daily.get(0).get("answerCount").asLong()).isZero();
        assertThat(daily.get(0).get("correctRate").isNull()).as("没作答的日子正确率为 null").isTrue();
        assertThat(daily.get(1).get("answerCount").asLong()).isEqualTo(1);
        assertThat(daily.get(1).get("correctRate").asDouble()).isEqualTo(1.0);
        assertThat(daily.get(2).get("answerCount").asLong()).isEqualTo(1);
        assertThat(daily.get(2).get("correctCount").asLong()).isZero();
        assertThat(stats(u, "daily")).as("默认 30 天").hasSize(30);

        JsonNode o = stats(u, "overview");
        assertThat(o.get("answerCount").asLong()).isEqualTo(2);
        assertThat(o.get("correctCount").asLong()).isEqualTo(1);
        assertThat(o.get("correctRate").asDouble()).isEqualTo(0.5);
        assertThat(o.get("studyDays").asLong()).isEqualTo(2);
        assertThat(o.get("todayAnswerCount").asLong()).isEqualTo(1);

        assertThat(call(get("/api/stats/daily?days=0", u)).status()).isEqualTo(400);
        assertThat(call(get("/api/stats/daily?days=366", u)).status()).isEqualTo(400);
    }

    @Test
    void streakCountsCompletedDailyUpToYesterdayWhenTodayIsOpen() throws Exception {
        Login u = register("stat2");
        Category[] t = tree("T");
        singles(t[2], 12);
        setTarget(u, t[0]);

        clock.set(Instant.parse("2026-10-31T02:00:00Z"));
        daily(u, 10);
        clock.set(Instant.parse("2026-11-01T02:00:00Z"));
        daily(u, 10);
        clock.set(Instant.parse("2026-11-02T02:00:00Z"));
        assertThat(stats(u, "overview").get("streakDays").asInt()).as("今天还没做，算到昨天").isEqualTo(2);
        daily(u, 9);
        assertThat(stats(u, "overview").get("streakDays").asInt()).as("没做完不算完成").isEqualTo(2);

        clock.set(Instant.parse("2026-11-02T03:00:00Z"));
        daily(u, 10); // 同一天返回同一组，补完剩下的
        assertThat(stats(u, "overview").get("streakDays").asInt()).isEqualTo(3);

        clock.set(Instant.parse("2026-11-04T02:00:00Z")); // 11-03 断了
        assertThat(stats(u, "overview").get("streakDays").asInt()).as("中断即归零").isZero();
        daily(u, 10);
        assertThat(stats(u, "overview").get("streakDays").asInt()).isEqualTo(1);
    }

    @Test
    void weakPointsUseLastAnswerPerQuestionAndNeedFiveQuestions() throws Exception {
        Login u = register("stat3");
        Category exam = category(null, "E", 0);
        Category subject = category(exam, "S", 0);
        Category a = category(subject, "A", 0);
        Category b = category(subject, "B", 1);
        Category c = category(subject, "C", 2);
        Category d = category(subject, "D", 3);
        List<Question> qa = singles(a, 5);
        List<Question> qb = singles(b, 4);
        List<Question> qc = singles(c, 5);
        List<Question> qd = singles(d, 5);
        setTarget(u, exam);

        clock.set(Instant.parse("2026-09-20T08:00:00Z")); // 超出近 30 天
        answerAll(u, d, qd, "B");
        clock.set(Instant.parse("2026-11-01T08:00:00Z"));
        answerAll(u, a, qa, "B");          // A：先全错
        answerAll(u, b, qb, "B");          // B：只有 4 道，不参与
        answerAll(u, c, qc, "A");          // C：全对
        clock.set(Instant.parse("2026-11-02T08:00:00Z"));
        answer(u, a, qa.get(0), "A");      // A 的第 1 题最后一次做对
        answer(u, a, qa.get(1), "A");

        JsonNode weak = stats(u, "weak");
        assertThat(ids(weak, "categoryId")).containsExactly(a.getId(), c.getId());
        assertThat(weak.get(0).get("sampleCount").asInt()).isEqualTo(5);
        assertThat(weak.get(0).get("correctRate").asDouble()).as("每题只看最后一次：2/5").isEqualTo(0.4);
        assertThat(weak.get(0).get("categoryPath").asText()).isEqualTo("E / S / A");
        assertThat(weak.get(1).get("correctRate").asDouble()).isEqualTo(1.0);
        assertThat(ids(stats(u, "weak?limit=1"), "categoryId")).containsExactly(a.getId());
        assertThat(ids(stats(u, "weak?days=60"), "categoryId")).as("放宽到 60 天后 D 进来")
                .containsExactly(d.getId(), a.getId(), c.getId());
        assertThat(call(get("/api/stats/weak?limit=0", u)).status()).isEqualTo(400);
    }

    @Test
    void chapterStatsAndScope() throws Exception {
        Login u = register("stat4");
        Category[] t = tree("T");
        Category[] other = tree("O");
        List<Question> qs = singles(t[2], 3);
        List<Question> qo = singles(other[2], 2);
        setTarget(u, t[0]);
        answer(u, t[2], qs.get(0), "A");
        answer(u, t[2], qs.get(0), "B");
        answer(u, t[2], qs.get(1), "A");
        answer(u, other[2], qo.get(0), "A");

        JsonNode cats = stats(u, "categories");
        assertThat(cats).hasSize(1);
        JsonNode ch = cats.get(0);
        assertThat(ch.get("categoryId").asLong()).isEqualTo(t[2].getId());
        assertThat(ch.get("categoryPath").asText()).isEqualTo("T / T-科目 / T-章节");
        assertThat(ch.get("questionCount").asLong()).isEqualTo(3);
        assertThat(ch.get("doneCount").asLong()).isEqualTo(2);
        assertThat(ch.get("answerCount").asLong()).isEqualTo(3);
        assertThat(ch.get("correctRate").asDouble()).isEqualTo(0.6667);

        assertThat(stats(u, "overview").get("answerCount").asLong()).as("默认只算目标考试").isEqualTo(3);
        assertThat(stats(u, "overview?categoryId=" + other[0].getId()).get("answerCount").asLong()).isEqualTo(1);
        assertThat(stats(u, "categories?categoryId=" + other[0].getId()).get(0).get("doneCount").asLong())
                .isEqualTo(1);
        Resp bad = call(get("/api/stats/overview?categoryId=" + t[2].getId(), u));
        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.data().get("fields").get(0).get("field").asText()).isEqualTo("categoryId");
    }

    @Test
    void examScoresAndUnansweredAreNotCountedAsAnswers() throws Exception {
        Login u = register("stat5");
        Category[] t = tree("T");
        List<Question> qs = singles(t[2], 4);
        setTarget(u, t[0]);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        ExamPaper p = new ExamPaper().setTitle("卷一").setCategoryId(t[0].getId()).setMode("FIXED")
                .setDurationMin(30).setTotalScore(40).setPassScore(20).setStatus("ONLINE")
                .setCreatedAt(now).setUpdatedAt(now);
        paperMapper.insert(p);
        for (int i = 0; i < qs.size(); i++) {
            paperQuestionMapper.insert(new PaperQuestion().setPaperId(p.getId()).setQuestionId(qs.get(i).getId())
                    .setScore(10).setSort(i));
        }

        long r1 = data(call(post("/api/exams/" + p.getId() + "/start", u, null))).get("recordId").asLong();
        call(put("/api/exam-records/" + r1 + "/answers/" + qs.get(0).getId(), u,
                Map.of("userAnswer", List.of("A"), "version", 1)));
        clock.advance(Duration.ofMinutes(5));
        call(post("/api/exam-records/" + r1 + "/submit", u, null));
        clock.advance(Duration.ofMinutes(5));
        long r2 = data(call(post("/api/exams/" + p.getId() + "/start", u, null))).get("recordId").asLong();
        for (Question q : qs) {
            call(put("/api/exam-records/" + r2 + "/answers/" + q.getId(), u,
                    Map.of("userAnswer", List.of("A"), "version", 1)));
        }
        call(post("/api/exam-records/" + r2 + "/submit", u, null));

        JsonNode o = stats(u, "overview");
        assertThat(o.get("examCount").asLong()).isEqualTo(2);
        assertThat(o.get("answerCount").asLong()).as("考试里没答的 3 题不算作答").isEqualTo(5);

        JsonNode exams = stats(u, "exams");
        assertThat(ids(exams, "recordId")).containsExactly(r1, r2);
        assertThat(exams.get(0).get("title").asText()).isEqualTo("卷一");
        assertThat(exams.get(0).get("score").asInt()).isEqualTo(10);
        assertThat(exams.get(0).get("rate").asDouble()).isEqualTo(0.25);
        assertThat(exams.get(1).get("passScore").asInt()).isEqualTo(20);
        assertThat(exams.get(0).get("submittedAt").asText()).isEqualTo("2026-11-02T08:05:00.000Z");
        assertThat(stats(u, "exams?paperId=999")).isEmpty();
    }

    @Test
    void weeklyLeaderboard() throws Exception {
        Category[] t = tree("T");
        List<Question> qs = singles(t[2], 4);
        Login a = register("rank_a");
        Login b = register("rank_b");
        Login c = register("rank_c");
        Login d = register("rank_d");
        Login idle = register("rank_idle");
        for (Login l : List.of(a, b, c, d, idle)) {
            setTarget(l, t[0]);
        }

        clock.set(Instant.parse("2026-11-01T15:59:00Z")); // 北京时间上周日 23:59，不算本周
        answerAll(c, t[2], qs, "A");
        clock.set(Instant.parse("2026-11-01T16:00:00Z")); // 本周一 00:00
        answerAll(a, t[2], qs.subList(0, 3), "A");
        answerAll(b, t[2], qs.subList(0, 3), "B");
        answer(c, t[2], qs.get(0), "A");
        answerAll(d, t[2], qs, "A");
        jdbc.update("UPDATE app_user SET status = 'DISABLED' WHERE id = ?", d.id());
        clock.set(Instant.parse("2026-11-04T08:00:00Z"));

        JsonNode board = data(call(get("/api/stats/leaderboard?period=WEEK", c)));
        JsonNode items = board.get("items");
        assertThat(items).as("禁用用户不上榜").hasSize(3);
        assertThat(items.findValuesAsText("nickname")).containsExactly("rank_a", "rank_b", "rank_c");
        assertThat(items.get(0).get("rank").asInt()).isEqualTo(1);
        assertThat(items.get(1).get("rank").asInt()).as("并列同名次").isEqualTo(1);
        assertThat(items.get(2).get("rank").asInt()).isEqualTo(3);
        assertThat(items.get(0).get("answerCount").asLong()).isEqualTo(3);
        assertThat(items.get(2).get("answerCount").asLong()).isEqualTo(1);
        assertThat(items.get(0).has("avatarUrl")).isTrue();
        assertThat(board.get("me").get("rank").asInt()).isEqualTo(3);
        assertThat(board.get("me").get("answerCount").asLong()).isEqualTo(1);

        JsonNode idleBoard = data(call(get("/api/stats/leaderboard", idle)));
        assertThat(idleBoard.get("me").get("rank").isNull()).isTrue();
        assertThat(idleBoard.get("me").get("answerCount").asLong()).isZero();
        assertThat(call(get("/api/stats/leaderboard?period=MONTH", a)).status()).isEqualTo(400);

        clock.set(Instant.parse("2026-11-09T08:00:00Z")); // 下一周
        assertThat(data(call(get("/api/stats/leaderboard", a))).get("items")).isEmpty();
    }
}
