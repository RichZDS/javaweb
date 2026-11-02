package com.richzds.quiz.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import com.richzds.quiz.domain.FillOptions;
import com.richzds.quiz.entity.Category;
import com.richzds.quiz.entity.Question;
import com.richzds.quiz.support.ApiTestBase;

/** 练习：抽题、题不够、复用、提交一次、背题模式、范围、结束、记录、重做错题。 */
class PracticeApiTest extends ApiTestBase {

    private Resp start(Login u, Map<String, Object> body) throws Exception {
        return call(post("/api/practice/sessions", u, body));
    }

    private Resp submit(Login u, long sessionId, long questionId, List<String> answer) throws Exception {
        return call(post("/api/practice/sessions/" + sessionId + "/answers", u,
                Map.of("questionId", questionId, "userAnswer", answer, "timeSpentSec", 7)));
    }

    private static Map<String, Object> random(Category c, int count) {
        Map<String, Object> m = new HashMap<>();
        m.put("mode", "RANDOM");
        m.put("categoryIds", List.of(c.getId()));
        m.put("count", count);
        return m;
    }

    private long answerRecords(Login u) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM answer_record WHERE user_id = ?", Long.class, u.id());
    }

    @Test
    void randomDrawsDistinctQuestionsFromSubtree() throws Exception {
        Login u = register("rand1");
        Category exam = category(null, "E", 0);
        Category subject = category(exam, "S", 0);
        Category c1 = category(subject, "C1", 0);
        Category c2 = category(subject, "C2", 1);
        singles(c1, 8);
        singles(c2, 7);
        Category[] other = tree("other");
        singles(other[2], 5);

        Resp r = start(u, random(subject, 10));
        assertThat(r.status()).isEqualTo(201);
        JsonNode s = r.data();
        assertThat(s.get("mode").asText()).isEqualTo("RANDOM");
        assertThat(s.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(s.get("questionCount").asInt()).isEqualTo(10);
        assertThat(s.get("answeredCount").asInt()).isZero();
        assertThat(s.get("currentIndex").asInt()).isZero();
        assertThat(s.get("startedAt").asText()).isEqualTo("2026-11-02T08:00:00.000Z");
        assertThat(s.get("completedAt").isNull()).isTrue();
        List<Long> ids = ids(s.get("questions"), "id");
        assertThat(new HashSet<>(ids)).hasSize(10);
        s.get("questions").forEach(q -> {
            assertThat(q.get("categoryId").asLong()).isIn(c1.getId(), c2.getId());
            assertThat(q.get("submitted").asBoolean()).isFalse();
            assertThat(q.has("correctAnswer")).as("没提交的题不带答案").isFalse();
            assertThat(q.has("analysisHtml")).isFalse();
            assertThat(q.has("stemHtml")).isTrue();
        });
    }

    @Test
    void notEnoughQuestionsIs409WithAvailableUnlessAcceptFewer() throws Exception {
        Login u = register("pool1");
        Category[] t = tree("T");
        singles(t[2], 5);

        Resp r = start(u, random(t[2], 10));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("POOL_INSUFFICIENT");
        assertThat(r.data().get("available").asInt()).isEqualTo(5);

        Map<String, Object> fewer = random(t[2], 10);
        fewer.put("acceptFewer", true);
        Resp ok = start(u, fewer);
        assertThat(ok.status()).isEqualTo(201);
        assertThat(ok.data().get("questionCount").asInt()).isEqualTo(5);

        Category empty = category(t[1], "空章节", 9);
        Map<String, Object> none = random(empty, 10);
        none.put("acceptFewer", true);
        Resp zero = start(u, none);
        assertThat(zero.status()).as("0 道题始终 409").isEqualTo(409);
        assertThat(zero.data().get("available").asInt()).isZero();
    }

    @Test
    void invalidRequestsAre400() throws Exception {
        Login u = register("bad1");
        Category[] t = tree("T");
        singles(t[2], 10);
        assertThat(start(u, random(t[2], 7)).status()).isEqualTo(400);
        assertThat(start(u, Map.of("mode", "NOPE", "categoryIds", List.of(t[2].getId()), "count", 10)).status())
                .isEqualTo(400);
        assertThat(start(u, Map.of("mode", "RANDOM", "categoryIds", List.of(999999), "count", 10)).status())
                .isEqualTo(400);
        assertThat(start(u, Map.of("mode", "RANDOM", "count", 10)).status()).as("没给分类也没目标考试")
                .isEqualTo(400);
    }

    @Test
    void typesFilter() throws Exception {
        Login u = register("types1");
        Category[] t = tree("T");
        singles(t[2], 3);
        for (int i = 0; i < 10; i++) {
            judge(t[2], true);
        }
        Map<String, Object> body = random(t[2], 10);
        body.put("types", List.of("JUDGE"));
        JsonNode s = data(start(u, body));
        s.get("questions").forEach(q -> assertThat(q.get("type").asText()).isEqualTo("JUDGE"));
    }

    @Test
    void eachQuestionCountsOnlyOnce() throws Exception {
        Login u = register("once1");
        Category[] t = tree("T");
        List<Question> qs = singles(t[2], 10);
        JsonNode s = data(start(u, random(t[2], 10)));
        long sid = s.get("id").asLong();
        long qid = s.get("questions").get(0).get("id").asLong();

        JsonNode first = data(submit(u, sid, qid, List.of("A")));
        assertThat(first.get("correct").asBoolean()).isTrue();
        assertThat(first.get("alreadySubmitted").asBoolean()).isFalse();
        assertThat(first.get("correctAnswer").toString()).isEqualTo("[\"A\"]");
        assertThat(first.get("acceptedAnswers").isNull()).isTrue();
        assertThat(first.get("analysisHtml").asText()).contains("<strong>原因</strong>");

        JsonNode again = data(submit(u, sid, qid, List.of("B")));
        assertThat(again.get("alreadySubmitted").asBoolean()).isTrue();
        assertThat(again.get("correct").asBoolean()).as("保持第一次的结果").isTrue();
        assertThat(again.get("userAnswer").toString()).isEqualTo("[\"A\"]");
        assertThat(answerRecords(u)).isEqualTo(1);

        JsonNode view = data(call(get("/api/practice/sessions/" + sid, u)));
        assertThat(view.get("answeredCount").asInt()).isEqualTo(1);
        assertThat(view.get("correctCount").asInt()).isEqualTo(1);
        JsonNode q0 = view.get("questions").get(0);
        assertThat(q0.get("submitted").asBoolean()).isTrue();
        assertThat(q0.get("userAnswer").toString()).isEqualTo("[\"A\"]");
        assertThat(q0.get("correct").asBoolean()).isTrue();
        assertThat(q0.get("correctAnswer").toString()).isEqualTo("[\"A\"]");
        assertThat(view.get("questions").get(1).has("correctAnswer")).isFalse();

        Question notInSession = single(t[2], "A");
        assertThat(submit(u, sid, notInSession.getId(), List.of("A")).status()).isEqualTo(400);
        assertThat(qs).hasSize(10);
    }

    @Test
    void cannotSeeOrSubmitOthersSession() throws Exception {
        Login owner = register("owner1");
        Login intruder = register("intruder1");
        Category[] t = tree("T");
        singles(t[2], 10);
        JsonNode s = data(start(owner, random(t[2], 10)));
        long sid = s.get("id").asLong();
        long qid = s.get("questions").get(0).get("id").asLong();

        assertThat(call(get("/api/practice/sessions/" + sid, intruder)).status()).isEqualTo(404);
        Resp r = submit(intruder, sid, qid, List.of("A"));
        assertThat(r.status()).isEqualTo(404);
        assertThat(r.code()).isEqualTo("NOT_FOUND");
        assertThat(call(post("/api/practice/sessions/" + sid + "/finish", intruder, null)).status()).isEqualTo(404);
        assertThat(call(get("/api/practice/sessions/999999", owner)).status()).isEqualTo(404);
    }

    @Test
    void reciteModeShowsAnswersButCannotSubmitAndIsNotCounted() throws Exception {
        Login u = register("recite1");
        Category[] t = tree("T");
        singles(t[2], 10);
        Map<String, Object> body = random(t[2], 10);
        body.put("reciteMode", true);
        JsonNode s = data(start(u, body));
        assertThat(s.get("reciteMode").asBoolean()).isTrue();
        JsonNode q0 = s.get("questions").get(0);
        assertThat(q0.get("submitted").asBoolean()).isFalse();
        assertThat(q0.get("correctAnswer").toString()).isEqualTo("[\"A\"]");
        assertThat(q0.get("analysisHtml").asText()).isNotBlank();

        Resp r = submit(u, s.get("id").asLong(), q0.get("id").asLong(), List.of("B"));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("CONFLICT");
        assertThat(answerRecords(u)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wrong_question WHERE user_id = ?", Long.class, u.id()))
                .isZero();
        JsonNode chapter = data(call(get("/api/categories?rootId=" + t[0].getId(), u)))
                .get("children").get(0).get("children").get(0);
        assertThat(chapter.get("doneCount").asLong()).isZero();

        // 背题模式的作答也不算“做过”：UNDONE 范围仍能抽到全部 10 道
        Map<String, Object> undone = random(t[2], 10);
        undone.put("scope", "UNDONE");
        assertThat(start(u, undone).status()).isEqualTo(201);
    }

    @Test
    void scopeUndoneAndWrongEver() throws Exception {
        Login u = register("scope1");
        Category[] t = tree("T");
        List<Question> qs = singles(t[2], 12);
        JsonNode s = data(start(u, random(t[2], 10)));
        long sid = s.get("id").asLong();
        List<Long> drawn = ids(s.get("questions"), "id");
        // 前 3 题答错，第 4 题答对
        for (int i = 0; i < 3; i++) {
            submit(u, sid, drawn.get(i), List.of("B"));
        }
        submit(u, sid, drawn.get(3), List.of("A"));

        Map<String, Object> undone = random(t[2], 10);
        undone.put("scope", "UNDONE");
        Resp r = start(u, undone);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.data().get("available").asInt()).isEqualTo(8);

        Map<String, Object> wrongEver = random(t[2], 10);
        wrongEver.put("scope", "WRONG_EVER");
        wrongEver.put("acceptFewer", true);
        JsonNode w = data(start(u, wrongEver));
        assertThat(ids(w.get("questions"), "id")).containsExactlyInAnyOrderElementsOf(drawn.subList(0, 3));

        // 某题后来做对了，就不再算“没全对过”；移出错题本不影响这个范围
        long sid2 = w.get("id").asLong();
        submit(u, sid2, drawn.get(0), List.of("A"));
        call(delete("/api/wrong-questions/" + drawn.get(1), u));
        JsonNode w2 = data(start(u, wrongEver));
        assertThat(ids(w2.get("questions"), "id")).containsExactlyInAnyOrder(drawn.get(1), drawn.get(2));
        assertThat(qs).hasSize(12);
    }

    @Test
    void sequentialTakesWholeChapterInOrderAndIsReused() throws Exception {
        Login u = register("seq1");
        Category[] t = tree("T");
        // 插入顺序和 sort 不一致，验证按 sort 排
        Question b = save(base(t[2], "JUDGE", "b").setAnswerJson("[\"T\"]").setSort(2));
        Question a = save(base(t[2], "JUDGE", "a").setAnswerJson("[\"T\"]").setSort(1));
        Question c = save(base(t[2], "JUDGE", "c").setAnswerJson("[\"T\"]").setSort(3));

        Map<String, Object> body = Map.of("mode", "SEQUENTIAL", "categoryIds", List.of(t[2].getId()), "count", 50);
        Resp first = start(u, body);
        assertThat(first.status()).isEqualTo(201);
        assertThat(ids(first.data().get("questions"), "id")).containsExactly(a.getId(), b.getId(), c.getId());
        long sid = first.data().get("id").asLong();

        assertThat(call(put("/api/practice/sessions/" + sid + "/progress", u, Map.of("currentIndex", 2))).status())
                .isEqualTo(200);
        assertThat(call(put("/api/practice/sessions/" + sid + "/progress", u, Map.of("currentIndex", 3))).status())
                .isEqualTo(400);

        Resp again = start(u, body);
        assertThat(again.status()).as("同一章节未完成的顺序练习直接返回").isEqualTo(200);
        assertThat(again.data().get("id").asLong()).isEqualTo(sid);
        assertThat(again.data().get("currentIndex").asInt()).isEqualTo(2);

        call(post("/api/practice/sessions/" + sid + "/finish", u, null));
        Resp third = start(u, body);
        assertThat(third.status()).isEqualTo(201);
        assertThat(third.data().get("id").asLong()).isNotEqualTo(sid);
    }

    @Test
    void dailyIsTenPerDayPrefersUndoneAndIsReused() throws Exception {
        Login u = register("daily1");
        Category[] t = tree("T");
        List<Question> qs = singles(t[2], 15);
        assertThat(start(u, Map.of("mode", "DAILY")).status()).as("没设目标考试").isEqualTo(400);
        setTarget(u, t[0]);

        // 先做掉 5 道，每日一练应优先抽剩下没做过的 10 道
        JsonNode warm = data(start(u, Map.of("mode", "SEQUENTIAL", "categoryIds", List.of(t[2].getId()))));
        List<Long> seq = ids(warm.get("questions"), "id");
        for (int i = 0; i < 5; i++) {
            submit(u, warm.get("id").asLong(), seq.get(i), List.of("A"));
        }

        Resp d1 = start(u, Map.of("mode", "DAILY", "categoryIds", List.of(999)));
        assertThat(d1.status()).isEqualTo(201);
        assertThat(d1.data().get("mode").asText()).isEqualTo("DAILY");
        assertThat(d1.data().get("dailyDate").asText()).isEqualTo("2026-11-02");
        List<Long> daily = ids(d1.data().get("questions"), "id");
        assertThat(daily).hasSize(10).doesNotContainAnyElementsOf(seq.subList(0, 5));

        Resp d2 = start(u, Map.of("mode", "DAILY"));
        assertThat(d2.status()).isEqualTo(200);
        assertThat(d2.data().get("id").asLong()).isEqualTo(d1.data().get("id").asLong());

        // 北京时间跨天（UTC 16:00）后是新的一组
        clock.set(Instant.parse("2026-11-02T15:59:00Z"));
        assertThat(start(u, Map.of("mode", "DAILY")).status()).isEqualTo(200);
        clock.set(Instant.parse("2026-11-02T16:00:00Z"));
        Resp next = start(u, Map.of("mode", "DAILY"));
        assertThat(next.status()).isEqualTo(201);
        assertThat(next.data().get("dailyDate").asText()).isEqualTo("2026-11-03");
        assertThat(qs).hasSize(15);
    }

    @Test
    void dailyCompletesOnlyWhenAllSubmitted() throws Exception {
        Login u = register("daily2");
        Category[] t = tree("T");
        singles(t[2], 10);
        setTarget(u, t[0]);
        JsonNode d = data(start(u, Map.of("mode", "DAILY")));
        long sid = d.get("id").asLong();
        List<Long> ids = ids(d.get("questions"), "id");
        submit(u, sid, ids.get(0), List.of("A"));

        JsonNode early = data(call(post("/api/practice/sessions/" + sid + "/finish", u, null)));
        assertThat(early.get("answeredCount").asInt()).isEqualTo(1);
        JsonNode stillOpen = data(call(get("/api/practice/sessions/" + sid, u)));
        assertThat(stillOpen.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(stillOpen.get("completedAt").isNull()).isTrue();

        for (Long id : ids.subList(1, ids.size())) {
            submit(u, sid, id, List.of("A"));
        }
        JsonNode done = data(call(get("/api/practice/sessions/" + sid, u)));
        assertThat(done.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(done.get("completedAt").asText()).isNotBlank();
    }

    @Test
    void finishSummaryAndAutoComplete() throws Exception {
        Login u = register("finish1");
        Category[] t = tree("T");
        singles(t[2], 10);
        JsonNode s = data(start(u, random(t[2], 10)));
        long sid = s.get("id").asLong();
        List<Long> ids = ids(s.get("questions"), "id");
        clock.advance(Duration.ofSeconds(95));
        submit(u, sid, ids.get(0), List.of("A"));
        submit(u, sid, ids.get(1), List.of("B"));
        submit(u, sid, ids.get(2), List.of("C"));

        JsonNode f = data(call(post("/api/practice/sessions/" + sid + "/finish", u, null)));
        assertThat(f.get("durationSec").asLong()).isEqualTo(95);
        assertThat(f.get("answeredCount").asInt()).isEqualTo(3);
        assertThat(f.get("correctCount").asInt()).isEqualTo(1);
        assertThat(f.get("correctRate").asDouble()).isEqualTo(0.3333);
        List<Long> wrongIds = new ArrayList<>();
        f.get("wrongQuestionIds").forEach(v -> wrongIds.add(v.asLong()));
        assertThat(wrongIds).containsExactly(ids.get(1), ids.get(2));

        Resp late = submit(u, sid, ids.get(3), List.of("A"));
        assertThat(late.status()).as("结束后不能再提交新题").isEqualTo(409);
        assertThat(submit(u, sid, ids.get(0), List.of("A")).data().get("alreadySubmitted").asBoolean()).isTrue();

        // 全部提交后自动完成
        JsonNode s2 = data(start(u, random(t[2], 10)));
        for (JsonNode q : s2.get("questions")) {
            submit(u, s2.get("id").asLong(), q.get("id").asLong(), List.of("A"));
        }
        assertThat(data(call(get("/api/practice/sessions/" + s2.get("id").asLong(), u))).get("status").asText())
                .isEqualTo("COMPLETED");
    }

    @Test
    void currentAndHistory() throws Exception {
        Login u = register("hist1");
        Category[] t = tree("T");
        singles(t[2], 10);
        assertThat(call(get("/api/practice/sessions/current", u)).data().isNull()).isTrue();

        JsonNode s1 = data(start(u, random(t[2], 10)));
        clock.advance(Duration.ofMinutes(1));
        JsonNode s2 = data(start(u, random(t[2], 10)));
        submit(u, s2.get("id").asLong(), s2.get("questions").get(0).get("id").asLong(), List.of("A"));
        setTarget(u, t[0]);
        clock.advance(Duration.ofMinutes(1));
        start(u, Map.of("mode", "DAILY"));

        JsonNode current = data(call(get("/api/practice/sessions/current", u)));
        assertThat(current.get("id").asLong()).as("每日一练不算“继续练习”").isEqualTo(s2.get("id").asLong());

        call(post("/api/practice/sessions/" + s2.get("id").asLong() + "/finish", u, null));
        assertThat(data(call(get("/api/practice/sessions/current", u))).get("id").asLong())
                .isEqualTo(s1.get("id").asLong());

        JsonNode page = data(call(get("/api/practice/sessions?page=1&size=2", u)));
        assertThat(page.get("total").asLong()).isEqualTo(3);
        assertThat(page.get("items")).hasSize(2);
        JsonNode item = page.get("items").get(1);
        assertThat(item.get("id").asLong()).isEqualTo(s2.get("id").asLong());
        assertThat(item.get("questionCount").asInt()).isEqualTo(10);
        assertThat(item.get("answeredCount").asInt()).isEqualTo(1);
        assertThat(item.get("correctCount").asInt()).isEqualTo(1);
        assertThat(item.get("completedAt").asText()).isNotBlank();
        assertThat(data(call(get("/api/practice/sessions?page=1&size=20", register("hist2")))).get("total").asLong())
                .isZero();
    }

    @Test
    void fillQuestionInPractice() throws Exception {
        Login u = register("fill1");
        Category[] t = tree("T");
        Question f = fill(t[2], new FillOptions(true, true), List.of(List.of("int", "Integer"), List.of("5")));
        JsonNode s = data(start(u, Map.of("mode", "SEQUENTIAL", "categoryIds", List.of(t[2].getId()))));
        JsonNode q = s.get("questions").get(0);
        assertThat(q.get("blankCount").asInt()).isEqualTo(2);
        JsonNode r = data(submit(u, s.get("id").asLong(), f.getId(), List.of(" INTEGER ", "5")));
        assertThat(r.get("correct").asBoolean()).isTrue();
        assertThat(r.get("correctAnswer").isNull()).isTrue();
        assertThat(r.get("acceptedAnswers").toString()).isEqualTo("[[\"int\",\"Integer\"],[\"5\"]]");
    }

    @Test
    void retryRedoesWrongOnesInOriginalOrder() throws Exception {
        Login u = register("retry1");
        Category[] t = tree("T");
        singles(t[2], 10);
        JsonNode s = data(start(u, random(t[2], 10)));
        long sid = s.get("id").asLong();
        List<Long> ids = ids(s.get("questions"), "id");
        submit(u, sid, ids.get(5), List.of("B"));
        submit(u, sid, ids.get(1), List.of("B"));
        submit(u, sid, ids.get(3), List.of("A"));

        Resp retry = start(u, Map.of("mode", "RETRY", "fromSessionId", sid, "count", 10));
        assertThat(retry.status()).isEqualTo(201);
        assertThat(retry.data().get("mode").asText()).isEqualTo("RETRY");
        assertThat(ids(retry.data().get("questions"), "id")).containsExactly(ids.get(1), ids.get(5));

        // 重做里答对按错题练习规则累加连对
        submit(u, retry.data().get("id").asLong(), ids.get(1), List.of("A"));
        assertThat(jdbc.queryForObject("SELECT correct_streak FROM wrong_question WHERE user_id = ? AND question_id = ?",
                Integer.class, u.id(), ids.get(1))).isEqualTo(1);

        JsonNode clean = data(start(u, random(t[2], 10)));
        submit(u, clean.get("id").asLong(), clean.get("questions").get(0).get("id").asLong(), List.of("A"));
        Resp none = start(u, Map.of("mode", "RETRY", "fromSessionId", clean.get("id").asLong()));
        assertThat(none.status()).isEqualTo(409);
        assertThat(none.code()).isEqualTo("POOL_INSUFFICIENT");
        assertThat(none.data().get("available").asInt()).isZero();

        assertThat(start(u, Map.of("mode", "RETRY")).status()).isEqualTo(400);
        assertThat(start(register("retry2"), Map.of("mode", "RETRY", "fromSessionId", sid)).status())
                .as("别人的练习").isEqualTo(404);
    }

    @Test
    void tagModeMatchesWholeTag() throws Exception {
        Login u = register("tag1");
        Category[] t = tree("T");
        for (int i = 0; i < 10; i++) {
            Question q = single(t[2], "A");
            q.setTags(i < 4 ? "链表,顺序表" : "链表实现");
            questionMapper.updateById(q);
        }
        Map<String, Object> body = new HashMap<>(Map.of("mode", "TAG", "tag", "链表",
                "categoryIds", List.of(t[0].getId()), "count", 10));
        Resp r = start(u, body);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.data().get("available").asInt()).as("“链表实现”不算“链表”").isEqualTo(4);
        assertThat(start(u, Map.of("mode", "TAG", "count", 10, "categoryIds", List.of(t[0].getId()))).status())
                .isEqualTo(400);
    }
}
