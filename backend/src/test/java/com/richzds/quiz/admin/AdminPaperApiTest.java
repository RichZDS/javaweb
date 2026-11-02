package com.richzds.quiz.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import com.richzds.quiz.entity.Category;
import com.richzds.quiz.entity.Question;
import com.richzds.quiz.support.ApiTestBase;

/** 后台试卷：建卷、算总分、上架校验、有人考过后的限制、删除。 */
class AdminPaperApiTest extends ApiTestBase {

    private static Map<String, Object> fixed(Category exam, List<Question> qs, int score, int pass) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", "固定卷");
        m.put("categoryId", exam.getId());
        m.put("description", "说明");
        m.put("mode", "FIXED");
        m.put("durationMin", 30);
        m.put("passScore", pass);
        m.put("totalScore", 999); // 传了也忽略
        m.put("questions", qs.stream().map(q -> Map.of("questionId", q.getId(), "score", score)).toList());
        return m;
    }

    private static Map<String, Object> rule(Category exam, List<Map<String, Object>> rules, int pass) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", "规则卷");
        m.put("categoryId", exam.getId());
        m.put("mode", "RULE");
        m.put("durationMin", 45);
        m.put("passScore", pass);
        m.put("rules", rules);
        return m;
    }

    @Test
    void createFixedPaperComputesTotalAndStartsOffline() throws Exception {
        Login admin = admin();
        Category[] t = tree("408");
        List<Question> qs = singles(t[2], 3);

        Resp r = call(post("/api/admin/papers", admin, fixed(t[0], qs, 5, 10)));
        assertThat(r.status()).isEqualTo(201);
        JsonNode p = r.data();
        assertThat(p.get("status").asText()).isEqualTo("OFFLINE");
        assertThat(p.get("totalScore").asInt()).isEqualTo(15);
        assertThat(p.get("categoryName").asText()).isEqualTo("408");
        assertThat(p.get("questionCount").asInt()).isEqualTo(3);
        assertThat(p.get("attempts").asLong()).isZero();
        assertThat(p.get("rules").isNull()).isTrue();
        JsonNode first = p.get("questions").get(0);
        assertThat(first.get("questionId").asLong()).isEqualTo(qs.get(0).getId());
        assertThat(first.get("score").asInt()).isEqualTo(5);
        assertThat(first.get("sort").asInt()).isZero();
        assertThat(first.get("type").asText()).isEqualTo("SINGLE");
        assertThat(first.get("stemPreview").asText()).isEqualTo("SINGLE 题干");

        long id = p.get("id").asLong();
        assertThat(data(call(get("/api/admin/papers/" + id, admin))).get("title").asText()).isEqualTo("固定卷");
        assertThat(data(call(get("/api/admin/papers?categoryId=" + t[0].getId() + "&status=OFFLINE", admin)))
                .get("total").asLong()).isEqualTo(1);
        assertThat(data(call(get("/api/exams?categoryId=" + t[0].getId(), register("pp_student"))))).as("下架的学员看不到")
                .isEmpty();
    }

    @Test
    void contentMustBelongToThePaperExam() throws Exception {
        Login admin = admin();
        Category[] t = tree("408");
        Category[] other = tree("Java");
        Question foreign = single(other[2], "A");
        Question draft = choice(t[2], "SINGLE", 4, List.of("A"), "DRAFT");

        Resp q1 = call(post("/api/admin/papers", admin, fixed(t[0], List.of(foreign), 5, 0)));
        assertThat(q1.status()).isEqualTo(400);
        assertThat(q1.data().get("fields").get(0).get("field").asText()).isEqualTo("questions[0].questionId");
        assertThat(call(post("/api/admin/papers", admin, fixed(t[0], List.of(draft), 5, 0))).status()).isEqualTo(400);
        Question ok = single(t[2], "A");
        assertThat(call(post("/api/admin/papers", admin, fixed(t[0], List.of(ok, ok), 5, 0))).status())
                .as("重复的题").isEqualTo(400);
        assertThat(call(post("/api/admin/papers", admin, fixed(t[1], List.of(ok), 5, 0))).status())
                .as("试卷分类必须是考试").isEqualTo(400);

        Resp badRule = call(post("/api/admin/papers", admin, rule(t[0],
                List.of(Map.of("categoryId", other[1].getId(), "type", "SINGLE", "count", 1, "score", 1)), 0)));
        assertThat(badRule.status()).isEqualTo(400);
        assertThat(badRule.data().get("fields").get(0).get("field").asText()).isEqualTo("rules[0].categoryId");
        JsonNode levelOne = data(call(post("/api/admin/papers", admin, rule(t[0],
                List.of(Map.of("categoryId", t[0].getId(), "type", "SINGLE", "count", 1, "score", 4)), 0))));
        assertThat(levelOne.get("rules").get(0).get("categoryPath").asText()).as("规则可以用一级分类").isEqualTo("408");
        assertThat(levelOne.get("totalScore").asInt()).isEqualTo(4);
        assertThat(levelOne.get("questions").isNull()).isTrue();
    }

    @Test
    void goingOnlineIsValidated() throws Exception {
        Login admin = admin();
        Category[] t = tree("408");
        List<Question> qs = singles(t[2], 3);
        judge(t[2], true);

        long empty = data(call(post("/api/admin/papers", admin, fixed(t[0], List.of(), 5, 0)))).get("id").asLong();
        Resp noQuestions = call(put("/api/admin/papers/" + empty + "/status", admin, Map.of("status", "ONLINE")));
        assertThat(noQuestions.status()).isEqualTo(409);
        assertThat(noQuestions.code()).isEqualTo("CONFLICT");

        long tooHigh = data(call(post("/api/admin/papers", admin, fixed(t[0], qs, 5, 16)))).get("id").asLong();
        assertThat(call(put("/api/admin/papers/" + tooHigh + "/status", admin, Map.of("status", "ONLINE"))).status())
                .as("及格分超过总分").isEqualTo(409);

        long ruleId = data(call(post("/api/admin/papers", admin, rule(t[0], List.of(
                Map.of("categoryId", t[0].getId(), "type", "SINGLE", "count", 2, "score", 5),
                Map.of("categoryId", t[2].getId(), "type", "SINGLE", "count", 2, "score", 5)), 10)))).get("id").asLong();
        Resp gaps = call(put("/api/admin/papers/" + ruleId + "/status", admin, Map.of("status", "ONLINE")));
        assertThat(gaps.status()).isEqualTo(409);
        assertThat(gaps.code()).isEqualTo("POOL_INSUFFICIENT");
        JsonNode gap = gaps.data().get("gaps").get(0);
        assertThat(gap.get("categoryId").asLong()).isEqualTo(t[0].getId());
        assertThat(gap.get("need").asInt()).as("两条规则重叠，不能重复抽题").isEqualTo(4);
        assertThat(gap.get("available").asInt()).isEqualTo(3);

        long okId = data(call(post("/api/admin/papers", admin, fixed(t[0], qs, 5, 10)))).get("id").asLong();
        JsonNode online = data(call(put("/api/admin/papers/" + okId + "/status", admin, Map.of("status", "ONLINE"))));
        assertThat(online.get("status").asText()).isEqualTo("ONLINE");
        Login student = register("pp_online");
        assertThat(ids(data(call(get("/api/exams?categoryId=" + t[0].getId(), student))), "paperId"))
                .containsExactly(okId);
        assertThat(data(call(put("/api/admin/papers/" + okId + "/status", admin, Map.of("status", "OFFLINE"))))
                .get("status").asText()).isEqualTo("OFFLINE");
        assertThat(call(put("/api/admin/papers/" + okId + "/status", admin, Map.of("status", "GONE"))).status())
                .isEqualTo(400);
    }

    @Test
    void attemptedPapersOnlyAllowTitleAndDescriptionAndCannotBeDeleted() throws Exception {
        Login admin = admin();
        Category[] t = tree("408");
        List<Question> qs = singles(t[2], 3);
        Map<String, Object> body = fixed(t[0], qs, 5, 10);
        long id = data(call(post("/api/admin/papers", admin, body))).get("id").asLong();

        body.put("questions", List.of(Map.of("questionId", qs.get(0).getId(), "score", 10)));
        JsonNode edited = data(call(put("/api/admin/papers/" + id, admin, body)));
        assertThat(edited.get("totalScore").asInt()).as("没人考过可以随便改").isEqualTo(10);
        call(put("/api/admin/papers/" + id + "/status", admin, Map.of("status", "ONLINE")));

        Login student = register("pp_taker");
        long rid = data(call(post("/api/exams/" + id + "/start", student, null))).get("recordId").asLong();
        assertThat(data(call(get("/api/admin/papers/" + id, admin))).get("attempts").asLong()).isEqualTo(1);

        body.put("title", "改名后的卷");
        body.put("description", "新说明");
        JsonNode renamed = data(call(put("/api/admin/papers/" + id, admin, body)));
        assertThat(renamed.get("title").asText()).isEqualTo("改名后的卷");
        assertThat(renamed.get("description").asText()).isEqualTo("新说明");

        body.put("durationMin", 99);
        Resp locked = call(put("/api/admin/papers/" + id, admin, body));
        assertThat(locked.status()).isEqualTo(409);
        assertThat(locked.code()).isEqualTo("CONFLICT");
        body.put("durationMin", 30);
        body.put("questions", List.of(Map.of("questionId", qs.get(1).getId(), "score", 10)));
        assertThat(call(put("/api/admin/papers/" + id, admin, body)).status()).isEqualTo(409);

        assertThat(call(delete("/api/admin/papers/" + id, admin)).status()).isEqualTo(409);
        assertThat(data(call(get("/api/exam-records/" + rid, student))).get("title").asText())
                .as("进行中的考试用开考时的快照标题").isEqualTo("固定卷");

        long fresh = data(call(post("/api/admin/papers", admin, fixed(t[0], qs, 5, 10)))).get("id").asLong();
        Resp deleted = call(delete("/api/admin/papers/" + fresh, admin));
        assertThat(deleted.status()).isEqualTo(200);
        assertThat(deleted.data().isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM paper_question WHERE paper_id = ?", Long.class, fresh))
                .isZero();
        assertThat(call(get("/api/admin/papers/" + fresh, admin)).status()).isEqualTo(404);
    }
}
