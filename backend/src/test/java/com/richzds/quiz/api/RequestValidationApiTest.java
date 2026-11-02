package com.richzds.quiz.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.richzds.quiz.entity.Category;
import com.richzds.quiz.support.ApiTestBase;

/**
 * 参数校验的回归测试：BUG-QA-001（搜题 keyword 缺失时 500）、BUG-QA-002（整数字段收到小数被截断）、
 * BUG-QA-003（请求体里字符串形式的数字 / 布尔被强转），以及各接口不带可选参数时都不能 500。
 */
class RequestValidationApiTest extends ApiTestBase {

    private void assertFieldError(Resp r, String field) {
        assertThat(r.status()).as(r.toString()).isEqualTo(400);
        assertThat(r.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(r.data().get("fields").get(0).get("field").asText()).isEqualTo(field);
    }

    // ---------------------------------------------------------------- BUG-QA-001

    @Test
    void searchKeywordIsRequired() throws Exception {
        Login u = register("valid1");
        Category[] t = tree("T");
        save(base(t[2], "JUDGE", "栈是后进先出").setAnswerJson("[\"T\"]"));

        assertFieldError(call(get("/api/questions/search", u)), "keyword");
        assertFieldError(call(get("/api/questions/search", u).param("keyword", "")), "keyword");
        assertFieldError(call(get("/api/questions/search", u).param("keyword", "   \t ")), "keyword");
        assertFieldError(call(get("/api/questions/search", u).param("keyword", "栈".repeat(51))), "keyword");
        assertFieldError(call(get("/api/questions/search?categoryId=" + t[0].getId(), u)), "keyword");

        Resp fifty = call(get("/api/questions/search", u).param("keyword", "栈".repeat(50)));
        assertThat(fifty.status()).isEqualTo(200);
        Resp padded = call(get("/api/questions/search", u).param("keyword", "  栈  "));
        assertThat(padded.data().get("total").asLong()).as("首尾空白去掉后再搜").isEqualTo(1);
    }

    // ---------------------------------------------------------------- BUG-QA-002

    @Test
    void fractionalIntegersAreRejectedWithFieldName() throws Exception {
        Login u = register("valid2");
        Category[] t = tree("T");
        singles(t[2], 10);

        assertFieldError(call(put("/api/me/settings", u, Map.of("wrongRemoveThreshold", 1.5))),
                "wrongRemoveThreshold");
        assertThat(data(call(get("/api/me", u))).get("wrongRemoveThreshold").asInt()).as("没有被改成 1").isEqualTo(3);

        Map<String, Object> body = new HashMap<>(Map.of("mode", "RANDOM", "categoryIds", List.of(t[2].getId())));
        body.put("count", 10.5);
        assertFieldError(call(post("/api/practice/sessions", u, body)), "count");
        body.put("count", "abc");
        assertFieldError(call(post("/api/practice/sessions", u, body)), "count");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM practice_session", Long.class)).isZero();

        body.put("count", 10);
        body.put("categoryIds", List.of(1.5));
        assertFieldError(call(post("/api/practice/sessions", u, body)), "categoryIds[0]");

        body.put("categoryIds", List.of(t[2].getId()));
        body.put("reciteMode", "maybe");
        assertFieldError(call(post("/api/practice/sessions", u, body)), "reciteMode");
        body.remove("reciteMode");
        assertThat(call(post("/api/practice/sessions", u, body)).status()).isEqualTo(201);

        assertFieldError(call(put("/api/practice/sessions/1/progress", u, Map.of("currentIndex", 0.5))),
                "currentIndex");
    }

    // ---------------------------------------------------------------- BUG-QA-003

    @Test
    void stringScalarsInBodyAreRejectedButQueryParamsStillWork() throws Exception {
        Login u = register("valid5");
        Category[] t = tree("T");
        singles(t[2], 10);

        assertFieldError(call(put("/api/me/settings", u, Map.of("wrongRemoveThreshold", "2"))),
                "wrongRemoveThreshold");
        assertThat(data(call(get("/api/me", u))).get("wrongRemoveThreshold").asInt()).isEqualTo(3);

        Map<String, Object> body = new HashMap<>(Map.of("mode", "RANDOM", "categoryIds", List.of(t[2].getId()),
                "count", "10"));
        assertFieldError(call(post("/api/practice/sessions", u, body)), "count");
        body.put("count", 10);
        body.put("reciteMode", "true");
        assertFieldError(call(post("/api/practice/sessions", u, body)), "reciteMode");
        body.put("reciteMode", true);
        body.put("categoryIds", List.of(String.valueOf(t[2].getId())));
        assertFieldError(call(post("/api/practice/sessions", u, body)), "categoryIds[0]");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM practice_session", Long.class)).isZero();

        // 查询参数本来就是字符串，照常转换
        JsonNode page = data(call(get("/api/practice/sessions?page=2&size=1", u)));
        assertThat(page.get("page").asLong()).isEqualTo(2);
        assertThat(page.get("size").asLong()).isEqualTo(1);
        assertThat(data(call(get("/api/stats/daily?days=7", u)))).hasSize(7);
        assertThat(data(call(get("/api/categories?rootId=" + t[0].getId(), u))).get("id").asLong())
                .isEqualTo(t[0].getId());
    }

    @Test
    void malformedJsonIs400() throws Exception {
        Login u = register("valid3");
        Resp r = call(post("/api/practice/sessions", u, null).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\": \"RANDOM\", "));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.code()).isEqualTo("VALIDATION_FAILED");
        Resp noBody = call(post("/api/practice/sessions", u, null).contentType(MediaType.APPLICATION_JSON));
        assertThat(noBody.status()).isEqualTo(400);
    }

    // ---------------------------------------------------------------- 可选参数不带时不能 500

    @Test
    void optionalParametersMayBeOmitted() throws Exception {
        Login u = register("valid4");
        Category[] t = tree("T");
        singles(t[2], 3);

        for (String path : List.of("/api/categories", "/api/wrong-questions", "/api/wrong-questions/groups",
                "/api/favorites", "/api/practice/sessions", "/api/practice/sessions/current", "/api/me")) {
            assertThat(call(get(path, u)).status()).as(path).isEqualTo(200);
        }
        assertFieldError(call(get("/api/app/version", null)), "platform");
        assertFieldError(call(get("/api/wrong-questions?sort=OLDEST", u)), "sort");
        assertFieldError(call(get("/api/questions/search?keyword=x&page=abc", u)), "page");
        assertFieldError(call(post("/api/practice/sessions", u, Map.of())), "mode");
        assertThat(call(put("/api/me", u, Map.of())).status()).isEqualTo(200);
        assertThat(call(put("/api/favorites/" + singles(t[2], 1).get(0).getId(), u, null)).status()).isEqualTo(200);
    }
}
