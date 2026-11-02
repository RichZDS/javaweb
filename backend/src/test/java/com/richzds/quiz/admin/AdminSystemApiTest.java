package com.richzds.quiz.admin;

import static org.assertj.core.api.Assertions.assertThat;

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

/** 后台用户管理、纠错处理、数据看板。 */
class AdminSystemApiTest extends ApiTestBase {

    @Autowired
    ExamPaperMapper paperMapper;
    @Autowired
    PaperQuestionMapper paperQuestionMapper;

    private void answer(Login u, Category chapter, Question q, String choice) throws Exception {
        long sid = data(call(post("/api/practice/sessions", u,
                Map.of("mode", "SEQUENTIAL", "categoryIds", List.of(chapter.getId()))))).get("id").asLong();
        call(post("/api/practice/sessions/" + sid + "/answers", u,
                Map.of("questionId", q.getId(), "userAnswer", List.of(choice))));
        call(post("/api/practice/sessions/" + sid + "/finish", u, null));
    }

    @Test
    void usersListAndStatus() throws Exception {
        Login admin = admin();
        Category[] t = tree("T");
        Question q = single(t[2], "A");
        Login alice = register("alice_adm");
        Login bob = register("bob_adm");
        answer(alice, t[2], q, "A");
        answer(alice, t[2], q, "B");

        JsonNode page = data(call(get("/api/admin/users", admin)));
        assertThat(page.get("total").asLong()).isEqualTo(3);
        JsonNode found = data(call(get("/api/admin/users?keyword=alice", admin))).get("items");
        assertThat(found).hasSize(1);
        JsonNode a = found.get(0);
        assertThat(a.get("username").asText()).isEqualTo("alice_adm");
        assertThat(a.get("role").asText()).isEqualTo("USER");
        assertThat(a.get("answerCount").asLong()).isEqualTo(2);
        assertThat(a.get("lastActiveAt").asText()).isEqualTo("2026-11-02T08:00:00.000Z");
        assertThat(a.has("passwordHash")).isFalse();

        JsonNode disabled = data(call(put("/api/admin/users/" + bob.id() + "/status", admin, Map.of("status", "DISABLED"))));
        assertThat(disabled.get("status").asText()).isEqualTo("DISABLED");
        assertThat(call(get("/api/me", bob)).status()).as("禁用立即生效").isEqualTo(403);
        data(call(put("/api/admin/users/" + bob.id() + "/status", admin, Map.of("status", "ACTIVE"))));
        assertThat(call(get("/api/me", bob)).status()).isEqualTo(200);

        Resp self = call(put("/api/admin/users/" + admin.id() + "/status", admin, Map.of("status", "DISABLED")));
        assertThat(self.status()).isEqualTo(409);
        assertThat(call(put("/api/admin/users/999999/status", admin, Map.of("status", "DISABLED"))).status())
                .isEqualTo(404);
        assertThat(call(get("/api/admin/users", alice)).status()).isEqualTo(403);
    }

    @Test
    void feedbackListAndHandle() throws Exception {
        Login admin = admin();
        Category[] t = tree("T");
        Question q = single(t[2], "A");
        Login u = register("fb_user");
        long f1 = data(call(post("/api/questions/" + q.getId() + "/feedback", u,
                Map.of("type", "ANSWER_WRONG", "content", "答案应为 B")))).get("id").asLong();
        clock.set(Instant.parse("2026-11-02T09:00:00Z"));
        call(post("/api/questions/" + q.getId() + "/feedback", u, Map.of("type", "OTHER", "content", "错别字")));

        JsonNode pending = data(call(get("/api/admin/feedback?status=PENDING", admin)));
        assertThat(pending.get("total").asLong()).isEqualTo(2);
        JsonNode older = pending.get("items").get(1);
        assertThat(older.get("id").asLong()).isEqualTo(f1);
        assertThat(older.get("questionId").asLong()).isEqualTo(q.getId());
        assertThat(older.get("stemPreview").asText()).isEqualTo("SINGLE 题干");
        assertThat(older.get("categoryPath").asText()).isEqualTo("T / T-科目 / T-章节");
        assertThat(older.get("type").asText()).isEqualTo("ANSWER_WRONG");
        assertThat(older.get("questionType").asText()).isEqualTo("SINGLE");
        assertThat(older.get("username").asText()).isEqualTo("fb_user");
        assertThat(older.get("handledAt").isNull()).isTrue();

        JsonNode handled = data(call(put("/api/admin/feedback/" + f1, admin,
                Map.of("status", "RESOLVED", "reply", " 已改正 "))));
        assertThat(handled.get("status").asText()).isEqualTo("RESOLVED");
        assertThat(handled.get("reply").asText()).isEqualTo("已改正");
        assertThat(handled.get("handledAt").asText()).isEqualTo("2026-11-02T09:00:00.000Z");
        assertThat(data(call(get("/api/admin/feedback?status=PENDING", admin))).get("total").asLong()).isEqualTo(1);
        assertThat(data(call(get("/api/admin/feedback", admin))).get("total").asLong()).isEqualTo(2);
        assertThat(call(put("/api/admin/feedback/" + f1, admin, Map.of("status", "PENDING"))).status()).isEqualTo(400);
        assertThat(call(get("/api/admin/feedback?status=DONE", admin)).status()).isEqualTo(400);
        assertThat(call(put("/api/admin/feedback/999999", admin, Map.of("status", "REJECTED"))).status()).isEqualTo(404);
    }

    @Test
    void dashboard() throws Exception {
        Login admin = admin();
        Category[] t = tree("T");
        List<Question> qs = singles(t[2], 2);
        choice(t[2], "SINGLE", 4, List.of("A"), "DRAFT");
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        ExamPaper p = new ExamPaper().setTitle("卷").setCategoryId(t[0].getId()).setMode("FIXED").setDurationMin(10)
                .setTotalScore(20).setPassScore(10).setStatus("ONLINE").setCreatedAt(now).setUpdatedAt(now);
        paperMapper.insert(p);
        for (Question q : qs) {
            paperQuestionMapper.insert(new PaperQuestion().setPaperId(p.getId()).setQuestionId(q.getId())
                    .setScore(10).setSort(0));
        }
        Login a = register("dash_a");
        Login b = register("dash_b");
        clock.set(Instant.parse("2026-11-01T08:00:00Z"));
        answer(a, t[2], qs.get(0), "A");
        clock.set(Instant.parse("2026-11-02T08:00:00Z"));
        answer(a, t[2], qs.get(0), "A");
        answer(b, t[2], qs.get(1), "A");
        for (Login l : List.of(a, b)) {
            long rid = data(call(post("/api/exams/" + p.getId() + "/start", l, null))).get("recordId").asLong();
            if (l == a) {
                call(put("/api/exam-records/" + rid + "/answers/" + qs.get(0).getId(), l,
                        Map.of("userAnswer", List.of("A"), "version", 1)));
            }
            call(post("/api/exam-records/" + rid + "/submit", l, null));
        }
        call(post("/api/questions/" + qs.get(0).getId() + "/feedback", a, Map.of("type", "OTHER", "content", "x")));

        JsonNode d = data(call(get("/api/admin/dashboard", admin)));
        assertThat(d.get("userCount").asLong()).as("只数学员").isEqualTo(2);
        assertThat(d.get("questionCount").asLong()).as("只数已发布").isEqualTo(2);
        assertThat(d.get("paperCount").asLong()).isEqualTo(1);
        assertThat(d.get("todayAnswerCount").asLong()).as("今天练习 2 条 + 考试里答了的 1 条").isEqualTo(3);
        assertThat(d.get("pendingFeedbackCount").asLong()).isEqualTo(1);
        JsonNode daily = d.get("daily");
        assertThat(daily).hasSize(14);
        assertThat(daily.get(13).get("date").asText()).isEqualTo("2026-11-02");
        assertThat(daily.get(13).get("answerCount").asLong()).isEqualTo(3);
        assertThat(daily.get(13).get("activeUsers").asLong()).isEqualTo(2);
        assertThat(daily.get(12).get("answerCount").asLong()).isEqualTo(1);
        assertThat(daily.get(12).get("activeUsers").asLong()).isEqualTo(1);
        JsonNode paper = d.get("papers").get(0);
        assertThat(paper.get("paperId").asLong()).isEqualTo(p.getId());
        assertThat(paper.get("attempts").asLong()).isEqualTo(2);
        assertThat(paper.get("avgScore").asDouble()).isEqualTo(5.0);
        assertThat(paper.get("passRate").asDouble()).isEqualTo(0.5);
    }
}
