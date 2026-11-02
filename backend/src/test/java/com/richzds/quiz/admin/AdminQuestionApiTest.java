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

/** 后台题目增删改查和预览。 */
class AdminQuestionApiTest extends ApiTestBase {

    private static Map<String, Object> multiple(Category chapter, String status) {
        Map<String, Object> m = new HashMap<>();
        m.put("categoryId", chapter.getId());
        m.put("type", "MULTIPLE");
        m.put("stem", "下列哪些是 **线性结构**？\n\n```java\nint[] a = new int[3];\n```");
        m.put("options", List.of(Map.of("key", "A", "text", "栈"), Map.of("key", "B", "text", "树"),
                Map.of("key", "C", "text", "队列")));
        m.put("correctAnswer", List.of("A", "C"));
        m.put("analysis", "栈和队列是线性结构。");
        m.put("difficulty", 2);
        m.put("tags", List.of("线性结构", "栈"));
        m.put("source", "原创");
        m.put("status", status);
        return m;
    }

    @Test
    void createAllTypesAndReadBack() throws Exception {
        Login admin = admin();
        Category[] t = tree("408");

        Resp created = call(post("/api/admin/questions", admin, multiple(t[2], "PUBLISHED")));
        assertThat(created.status()).isEqualTo(201);
        JsonNode q = created.data();
        assertThat(q.get("id").asLong()).isPositive();
        assertThat(q.get("categoryPath").asText()).isEqualTo("408 / 408-科目 / 408-章节");
        assertThat(q.get("stemHtml").asText()).contains("<strong>线性结构</strong>").contains("<pre style=");
        assertThat(q.get("options").get(2).get("html").asText()).isEqualTo("<p>队列</p>");
        assertThat(q.get("correctAnswer").toString()).isEqualTo("[\"A\",\"C\"]");
        assertThat(q.get("acceptedAnswers").isNull()).isTrue();
        assertThat(q.get("fillOptions").isNull()).isTrue();
        assertThat(q.get("analysisHtml").asText()).isEqualTo("<p>栈和队列是线性结构。</p>");
        assertThat(q.get("tags").toString()).isEqualTo("[\"线性结构\",\"栈\"]");
        assertThat(q.get("source").asText()).isEqualTo("原创");
        assertThat(q.get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(q.get("version").asInt()).isEqualTo(1);
        assertThat(q.get("sort").asInt()).isEqualTo(1);
        assertThat(q.get("answerCount").asLong()).isZero();
        assertThat(q.get("correctRate").isNull()).isTrue();
        assertThat(q.get("createdAt").asText()).isEqualTo("2026-11-02T08:00:00.000Z");

        Map<String, Object> judge = new HashMap<>(Map.of("categoryId", t[2].getId(), "type", "JUDGE",
                "stem", "栈是先进先出。", "correctAnswer", List.of("F"), "analysis", "栈是后进先出。",
                "status", "PUBLISHED"));
        JsonNode j = data(call(post("/api/admin/questions", admin, judge)));
        assertThat(j.get("options").toString()).contains("\"key\":\"T\"").contains("\"key\":\"F\"");
        assertThat(j.get("sort").asInt()).as("不传 sort 时排到章节末尾").isEqualTo(2);

        Map<String, Object> fill = new HashMap<>(Map.of("categoryId", t[2].getId(), "type", "FILL",
                "stem", "Java 整型关键字 ____", "acceptedAnswers", List.of(List.of(" int ", "Integer")),
                "analysis", "int。", "status", "PUBLISHED", "correctAnswer", List.of("A")));
        JsonNode f = data(call(post("/api/admin/questions", admin, fill)));
        assertThat(f.get("blankCount").asInt()).isEqualTo(1);
        assertThat(f.get("acceptedAnswers").toString()).isEqualTo("[[\"int\",\"Integer\"]]");
        assertThat(f.get("correctAnswer").isNull()).as("不适用的答案字段被忽略").isTrue();
        assertThat(f.get("fillOptions").get("ignoreCase").asBoolean()).isFalse();
        assertThat(f.get("options")).isEmpty();

        JsonNode detail = data(call(get("/api/admin/questions/" + q.get("id").asLong(), admin)));
        assertThat(detail.get("stem").asText()).startsWith("下列哪些是");
        assertThat(data(call(get("/api/questions/" + q.get("id").asLong(), register("adm_q_student")))).get("type")
                .asText()).as("发布的题学员能看到").isEqualTo("MULTIPLE");
    }

    @Test
    void validationRules() throws Exception {
        Login admin = admin();
        Category[] t = tree("408");

        Map<String, Object> noAnalysis = multiple(t[2], "PUBLISHED");
        noAnalysis.put("analysis", " ");
        assertField(call(post("/api/admin/questions", admin, noAnalysis)), "analysis");

        Map<String, Object> noAnswer = multiple(t[2], "PUBLISHED");
        noAnswer.remove("correctAnswer");
        assertField(call(post("/api/admin/questions", admin, noAnswer)), "correctAnswer");

        Map<String, Object> oneForMultiple = multiple(t[2], "PUBLISHED");
        oneForMultiple.put("correctAnswer", List.of("A"));
        assertField(call(post("/api/admin/questions", admin, oneForMultiple)), "correctAnswer");

        Map<String, Object> unknownKey = multiple(t[2], "PUBLISHED");
        unknownKey.put("correctAnswer", List.of("A", "E"));
        assertField(call(post("/api/admin/questions", admin, unknownKey)), "correctAnswer");

        Map<String, Object> badKeys = multiple(t[2], "PUBLISHED");
        badKeys.put("options", List.of(Map.of("key", "A", "text", "x"), Map.of("key", "C", "text", "y")));
        assertField(call(post("/api/admin/questions", admin, badKeys)), "options");

        Map<String, Object> notChapter = multiple(t[1], "PUBLISHED");
        assertField(call(post("/api/admin/questions", admin, notChapter)), "categoryId");

        Map<String, Object> archived = multiple(t[2], "ARCHIVED");
        assertField(call(post("/api/admin/questions", admin, archived)), "status");

        Map<String, Object> fillWithOptions = new HashMap<>(Map.of("categoryId", t[2].getId(), "type", "FILL",
                "stem", "x ____", "options", List.of(Map.of("key", "A", "text", "a")),
                "acceptedAnswers", List.of(List.of("a")), "analysis", "x", "status", "PUBLISHED"));
        assertField(call(post("/api/admin/questions", admin, fillWithOptions)), "options");

        Map<String, Object> emptyBlank = new HashMap<>(Map.of("categoryId", t[2].getId(), "type", "FILL",
                "stem", "x ____", "acceptedAnswers", List.of(List.of()), "analysis", "x", "status", "PUBLISHED"));
        assertField(call(post("/api/admin/questions", admin, emptyBlank)), "acceptedAnswers");

        Map<String, Object> badImage = multiple(t[2], "PUBLISHED");
        badImage.put("imageUrl", "javascript:alert(1)");
        assertField(call(post("/api/admin/questions", admin, badImage)), "imageUrl");

        // 草稿可以先不填答案和解析
        Map<String, Object> draft = new HashMap<>(Map.of("categoryId", t[2].getId(), "type", "SINGLE",
                "stem", "还没写完的题"));
        JsonNode d = data(call(post("/api/admin/questions", admin, draft)));
        assertThat(d.get("status").asText()).isEqualTo("DRAFT");
        assertThat(call(get("/api/questions/" + d.get("id").asLong(), register("adm_q_s2"))).status())
                .as("草稿学员看不到").isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM question", Long.class)).isEqualTo(1);
    }

    private static void assertField(Resp r, String field) {
        assertThat(r.status()).as(r.toString()).isEqualTo(400);
        assertThat(r.data().get("fields").get(0).get("field").asText()).isEqualTo(field);
    }

    @Test
    void updateBumpsVersionAndCanClearFields() throws Exception {
        Login admin = admin();
        Category[] t = tree("408");
        Map<String, Object> body = multiple(t[2], "PUBLISHED");
        body.put("imageUrl", "/uploads/2026/11/02/a.png");
        long id = data(call(post("/api/admin/questions", admin, body))).get("id").asLong();

        body.put("correctAnswer", List.of("A", "B", "C"));
        body.remove("imageUrl");
        body.put("status", "DRAFT");
        JsonNode updated = data(call(put("/api/admin/questions/" + id, admin, body)));
        assertThat(updated.get("version").asInt()).isEqualTo(2);
        assertThat(updated.get("correctAnswer").toString()).isEqualTo("[\"A\",\"B\",\"C\"]");
        assertThat(updated.get("imageUrl").isNull()).isTrue();
        assertThat(updated.get("status").asText()).isEqualTo("DRAFT");
        assertThat(updated.get("sort").asInt()).as("不传 sort 保持原值").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT image_url FROM question WHERE id = ?", String.class, id)).isNull();
        assertThat(call(put("/api/admin/questions/999999", admin, body)).status()).isEqualTo(404);
    }

    @Test
    void listFiltersAndAnswerStats() throws Exception {
        Login admin = admin();
        Category[] a = tree("A");
        Category[] b = tree("B");
        Question q1 = single(a[2], "A");
        Question q2 = choice(a[2], "MULTIPLE", 4, List.of("A", "B"), "DRAFT");
        single(b[2], "A");
        Login u = register("adm_list");
        long sid = data(call(post("/api/practice/sessions", u,
                Map.of("mode", "SEQUENTIAL", "categoryIds", List.of(a[2].getId()))))).get("id").asLong();
        call(post("/api/practice/sessions/" + sid + "/answers", u, Map.of("questionId", q1.getId(), "userAnswer", List.of("A"))));
        long sid2 = data(call(post("/api/practice/sessions", u,
                Map.of("mode", "RANDOM", "categoryIds", List.of(a[2].getId()), "count", 10, "acceptFewer", true))))
                .get("id").asLong();
        call(post("/api/practice/sessions/" + sid2 + "/answers", u, Map.of("questionId", q1.getId(), "userAnswer", List.of("B"))));

        JsonNode all = data(call(get("/api/admin/questions", admin)));
        assertThat(all.get("total").asLong()).isEqualTo(3);
        JsonNode inA = data(call(get("/api/admin/questions?categoryId=" + a[0].getId(), admin)));
        assertThat(ids(inA.get("items"), "id")).containsExactly(q2.getId(), q1.getId());
        JsonNode item = inA.get("items").get(1);
        assertThat(item.get("answerCount").asLong()).isEqualTo(2);
        assertThat(item.get("correctRate").asDouble()).isEqualTo(0.5);
        assertThat(item.get("stemPreview").asText()).isEqualTo("SINGLE 题干");
        assertThat(item.has("stemHtml")).isFalse();
        assertThat(item.has("analysisHtml")).isFalse();
        assertThat(item.get("options").get(0).has("html")).isTrue();

        assertThat(ids(data(call(get("/api/admin/questions?status=DRAFT", admin))).get("items"), "id"))
                .containsExactly(q2.getId());
        assertThat(ids(data(call(get("/api/admin/questions?type=MULTIPLE", admin))).get("items"), "id"))
                .containsExactly(q2.getId());
        assertThat(data(call(get("/api/admin/questions?keyword=MULTIPLE", admin))).get("total").asLong()).isEqualTo(1);
        assertThat(call(get("/api/admin/questions?status=GONE", admin)).status()).isEqualTo(400);
        assertThat(call(get("/api/admin/questions?type=ESSAY", admin)).status()).isEqualTo(400);
    }

    @Test
    void deleteRemovesUnusedAndArchivesUsed() throws Exception {
        Login admin = admin();
        Category[] t = tree("T");
        Question unused = single(t[2], "A");
        Question used = single(t[2], "A");
        Login u = register("adm_del");
        call(put("/api/favorites/" + unused.getId(), u, null));
        long sid = data(call(post("/api/practice/sessions", u,
                Map.of("mode", "SEQUENTIAL", "categoryIds", List.of(t[2].getId()))))).get("id").asLong();
        call(post("/api/practice/sessions/" + sid + "/answers", u, Map.of("questionId", used.getId(), "userAnswer", List.of("A"))));

        JsonNode d1 = data(call(delete("/api/admin/questions/" + unused.getId(), admin)));
        assertThat(d1.get("deleted").asBoolean()).isTrue();
        assertThat(d1.get("archived").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM question WHERE id = ?", Long.class, unused.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM favorite", Long.class)).as("收藏一起删").isZero();

        JsonNode d2 = data(call(delete("/api/admin/questions/" + used.getId(), admin)));
        assertThat(d2.get("deleted").asBoolean()).isFalse();
        assertThat(d2.get("archived").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM question WHERE id = ?", String.class, used.getId()))
                .isEqualTo("ARCHIVED");
        assertThat(call(get("/api/questions/" + used.getId(), u)).status()).as("归档后学员看不到").isEqualTo(404);
        assertThat(data(call(get("/api/practice/sessions/" + sid, u))).get("questions")).as("已做过的练习照常显示")
                .hasSize(1);
        assertThat(call(delete("/api/admin/questions/999999", admin)).status()).isEqualTo(404);
    }

    @Test
    void previewEscapesHtml() throws Exception {
        Login admin = admin();
        JsonNode p = data(call(post("/api/admin/questions/preview", admin,
                Map.of("markdown", "**粗** <img src=x onerror=alert(1)>\n\n```sql\nSELECT 1;\n```"))));
        assertThat(p.get("html").asText()).contains("<strong>粗</strong>").contains("&lt;img")
                .doesNotContain("<img").contains("<pre style=");
        assertThat(call(post("/api/admin/questions/preview", register("adm_prev"), Map.of("markdown", "x"))).status())
                .isEqualTo(403);
    }
}
