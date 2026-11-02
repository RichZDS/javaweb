package com.richzds.quiz.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import com.richzds.quiz.entity.Category;
import com.richzds.quiz.entity.Question;
import com.richzds.quiz.support.ApiTestBase;

/** 错题本：收录、累加、错题练习连对移出、移出后再错恢复、手动移出、排序和分组。 */
class WrongBookApiTest extends ApiTestBase {

    /** 用顺序练习做一次某章节里的一道题。 */
    private void answerOnce(Login u, Category chapter, Question q, String answer) throws Exception {
        JsonNode s = data(call(post("/api/practice/sessions", u,
                Map.of("mode", "SEQUENTIAL", "categoryIds", List.of(chapter.getId())))));
        long sid = s.get("id").asLong();
        data(call(post("/api/practice/sessions/" + sid + "/answers", u,
                Map.of("questionId", q.getId(), "userAnswer", List.of(answer)))));
        call(post("/api/practice/sessions/" + sid + "/finish", u, null));
    }

    /** 开一次错题练习并把里面这道题答对。 */
    private void correctInWrongPractice(Login u, Question q) throws Exception {
        JsonNode s = data(call(post("/api/practice/sessions", u, Map.of("mode", "WRONG"))));
        assertThat(ids(s.get("questions"), "id")).contains(q.getId());
        data(call(post("/api/practice/sessions/" + s.get("id").asLong() + "/answers", u,
                Map.of("questionId", q.getId(), "userAnswer", List.of("A")))));
    }

    private JsonNode entry(Login u, Question q) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT wrong_count, correct_streak, removed FROM wrong_question WHERE user_id = ? AND question_id = ?",
                u.id(), q.getId());
        return objectMapper.valueToTree(Map.of("wrongCount", ((Number) row.get("wrong_count")).intValue(),
                "correctStreak", ((Number) row.get("correct_streak")).intValue(),
                "removed", Boolean.TRUE.equals(row.get("removed")) || Integer.valueOf(1).equals(row.get("removed"))));
    }

    private JsonNode list(Login u, String query) throws Exception {
        return data(call(get("/api/wrong-questions" + query, u)));
    }

    @Test
    void wrongAnswerIsCollectedAndAccumulates() throws Exception {
        Login u = register("wrong1");
        Category[] t = tree("T");
        Question q = single(t[2], "A");
        answerOnce(u, t[2], q, "A");
        assertThat(list(u, "").get("total").asLong()).as("答对不收录").isZero();

        answerOnce(u, t[2], q, "B");
        JsonNode items = list(u, "").get("items");
        assertThat(items).hasSize(1);
        JsonNode item = items.get(0);
        assertThat(item.get("questionId").asLong()).isEqualTo(q.getId());
        assertThat(item.get("categoryPath").asText()).isEqualTo("T / T-科目 / T-章节");
        assertThat(item.get("type").asText()).isEqualTo("SINGLE");
        assertThat(item.get("stemPreview").asText()).isEqualTo("SINGLE 题干");
        assertThat(item.get("wrongCount").asInt()).isEqualTo(1);
        assertThat(item.get("lastWrongAnswer").toString()).isEqualTo("[\"B\"]");
        assertThat(item.get("lastWrongAt").asText()).isEqualTo("2026-11-02T08:00:00.000Z");

        clock.advance(Duration.ofHours(1));
        answerOnce(u, t[2], q, "C");
        item = list(u, "").get("items").get(0);
        assertThat(item.get("wrongCount").asInt()).isEqualTo(2);
        assertThat(item.get("lastWrongAnswer").toString()).isEqualTo("[\"C\"]");
        assertThat(item.get("lastWrongAt").asText()).isEqualTo("2026-11-02T09:00:00.000Z");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wrong_question WHERE user_id = ?", Long.class, u.id()))
                .as("同一题只一条").isEqualTo(1);
    }

    @Test
    void threeCorrectInWrongPracticeRemovesAndWrongAgainRestores() throws Exception {
        Login u = register("wrong2");
        Category[] t = tree("T");
        Question q = single(t[2], "A");
        answerOnce(u, t[2], q, "B");

        correctInWrongPractice(u, q);
        correctInWrongPractice(u, q);
        assertThat(entry(u, q).get("correctStreak").asInt()).isEqualTo(2);
        assertThat(entry(u, q).get("removed").asBoolean()).isFalse();
        correctInWrongPractice(u, q);
        assertThat(entry(u, q).get("removed").asBoolean()).as("连对 3 次移出").isTrue();
        assertThat(list(u, "").get("total").asLong()).isZero();
        Resp empty = call(post("/api/practice/sessions", u, Map.of("mode", "WRONG")));
        assertThat(empty.status()).isEqualTo(409);
        assertThat(empty.data().get("available").asInt()).isZero();

        answerOnce(u, t[2], q, "D");
        JsonNode back = entry(u, q);
        assertThat(back.get("removed").asBoolean()).as("移出后再错重新收录").isFalse();
        assertThat(back.get("wrongCount").asInt()).isEqualTo(2);
        assertThat(back.get("correctStreak").asInt()).isZero();
        assertThat(list(u, "").get("total").asLong()).isEqualTo(1);
    }

    @Test
    void correctOutsideWrongPracticeDoesNotCountAndWrongAnywhereResets() throws Exception {
        Login u = register("wrong3");
        Category[] t = tree("T");
        Question q = single(t[2], "A");
        answerOnce(u, t[2], q, "B");
        answerOnce(u, t[2], q, "A");
        assertThat(entry(u, q).get("correctStreak").asInt()).as("普通练习答对不累加").isZero();

        correctInWrongPractice(u, q);
        correctInWrongPractice(u, q);
        answerOnce(u, t[2], q, "C");
        assertThat(entry(u, q).get("correctStreak").asInt()).as("别处答错清零").isZero();
        assertThat(entry(u, q).get("wrongCount").asInt()).isEqualTo(2);
    }

    @Test
    void thresholdFollowsUserSetting() throws Exception {
        Login u = register("wrong4");
        Category[] t = tree("T");
        Question q = single(t[2], "A");
        data(call(put("/api/me/settings", u, Map.of("wrongRemoveThreshold", 1))));
        answerOnce(u, t[2], q, "B");
        correctInWrongPractice(u, q);
        assertThat(entry(u, q).get("removed").asBoolean()).isTrue();
    }

    @Test
    void manualRemoveSortAndGroups() throws Exception {
        Login u = register("wrong5");
        Category exam = category(null, "E", 0);
        Category subject = category(exam, "S", 0);
        Category c1 = category(subject, "C1", 0);
        Category c2 = category(subject, "C2", 1);
        Question a = single(c1, "A");
        Question b = single(c2, "A");
        Question c = single(c2, "A");
        answerOnce(u, c1, a, "B");
        answerOnce(u, c1, a, "B");
        answerOnce(u, c1, a, "B");
        clock.advance(Duration.ofMinutes(5));
        answerOnce(u, c2, b, "B");
        clock.advance(Duration.ofMinutes(5));
        answerOnce(u, c2, c, "B");

        assertThat(ids(list(u, "?sort=RECENT").get("items"), "questionId"))
                .containsExactly(c.getId(), b.getId(), a.getId());
        assertThat(ids(list(u, "?sort=MOST").get("items"), "questionId"))
                .containsExactly(a.getId(), c.getId(), b.getId());
        assertThat(ids(list(u, "?categoryId=" + c2.getId()).get("items"), "questionId"))
                .containsExactly(c.getId(), b.getId());
        assertThat(list(u, "?categoryId=" + exam.getId()).get("total").asLong()).isEqualTo(3);

        JsonNode groups = data(call(get("/api/wrong-questions/groups", u)));
        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).get("categoryId").asLong()).isEqualTo(c1.getId());
        assertThat(groups.get(0).get("categoryPath").asText()).isEqualTo("E / S / C1");
        assertThat(groups.get(0).get("count").asLong()).isEqualTo(1);
        assertThat(groups.get(1).get("count").asLong()).isEqualTo(2);

        Resp removed = call(delete("/api/wrong-questions/" + a.getId(), u));
        assertThat(removed.status()).isEqualTo(200);
        assertThat(removed.data().isNull()).isTrue();
        assertThat(list(u, "").get("total").asLong()).isEqualTo(2);
        assertThat(entry(u, a).get("wrongCount").asInt()).as("只改状态不删历史").isEqualTo(3);
        assertThat(call(delete("/api/wrong-questions/" + a.getId(), u)).status()).isEqualTo(404);
        assertThat(call(delete("/api/wrong-questions/" + b.getId(), register("wrong5b"))).status())
                .as("别人的错题").isEqualTo(404);
    }
}
