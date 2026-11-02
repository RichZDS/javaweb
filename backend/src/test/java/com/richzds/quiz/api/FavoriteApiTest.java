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

/** 收藏和笔记，以及收藏练习。 */
class FavoriteApiTest extends ApiTestBase {

    @Test
    void favoriteNoteListAndRemove() throws Exception {
        Login u = register("fav1");
        Category[] a = tree("A");
        Category[] b = tree("B");
        Question q1 = single(a[2], "A");
        Question q2 = single(b[2], "A");

        JsonNode created = data(call(put("/api/favorites/" + q1.getId(), u, null)));
        assertThat(created.get("questionId").asLong()).isEqualTo(q1.getId());
        assertThat(created.get("note").isNull()).isTrue();
        clock.advance(Duration.ofMinutes(1));
        data(call(put("/api/favorites/" + q2.getId(), u, Map.of("note", "易错"))));

        JsonNode updated = data(call(put("/api/favorites/" + q1.getId(), u, Map.of("note", "  记住 O(1)  "))));
        assertThat(updated.get("note").asText()).isEqualTo("记住 O(1)");
        assertThat(data(call(put("/api/favorites/" + q1.getId(), u, Map.of()))).get("note").asText())
                .as("不传 note 不改笔记").isEqualTo("记住 O(1)");

        JsonNode page = data(call(get("/api/favorites", u)));
        assertThat(page.get("total").asLong()).isEqualTo(2);
        assertThat(ids(page.get("items"), "questionId")).containsExactly(q2.getId(), q1.getId());
        JsonNode first = page.get("items").get(1);
        assertThat(first.get("categoryPath").asText()).isEqualTo("A / A-科目 / A-章节");
        assertThat(first.get("type").asText()).isEqualTo("SINGLE");
        assertThat(first.get("stemPreview").asText()).isEqualTo("SINGLE 题干");
        assertThat(first.get("createdAt").asText()).isEqualTo("2026-11-02T08:00:00.000Z");
        assertThat(data(call(get("/api/favorites?categoryId=" + a[0].getId(), u))).get("total").asLong())
                .isEqualTo(1);

        JsonNode detail = data(call(get("/api/questions/" + q1.getId(), u)));
        assertThat(detail.get("favorited").asBoolean()).isTrue();
        assertThat(detail.get("note").asText()).isEqualTo("记住 O(1)");

        assertThat(call(put("/api/favorites/" + q1.getId(), u, Map.of("note", "x".repeat(201)))).status())
                .isEqualTo(400);
        assertThat(call(put("/api/favorites/999999", u, null)).status()).isEqualTo(404);

        Resp removed = call(delete("/api/favorites/" + q1.getId(), u));
        assertThat(removed.status()).isEqualTo(200);
        assertThat(removed.data().isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM favorite WHERE user_id = ?", Long.class, u.id()))
                .isEqualTo(1);
        assertThat(data(call(get("/api/favorites", register("fav2")))).get("total").asLong()).isZero();
    }

    @Test
    void favoritePracticeUsesOnlyMyFavorites() throws Exception {
        Login u = register("fav3");
        Category[] t = tree("T");
        Question q1 = single(t[2], "A");
        Question q2 = single(t[2], "A");
        single(t[2], "A");
        call(put("/api/favorites/" + q1.getId(), u, null));
        call(put("/api/favorites/" + q2.getId(), u, null));

        JsonNode s = data(call(post("/api/practice/sessions", u, Map.of("mode", "FAVORITE"))));
        assertThat(ids(s.get("questions"), "id")).containsExactlyInAnyOrder(q1.getId(), q2.getId());
        s.get("questions").forEach(q -> assertThat(q.get("favorited").asBoolean()).isTrue());

        Resp fixedCount = call(post("/api/practice/sessions", u, Map.of("mode", "FAVORITE", "count", 10)));
        assertThat(fixedCount.status()).isEqualTo(409);
        assertThat(fixedCount.data().get("available").asInt()).isEqualTo(2);
        assertThat(call(post("/api/practice/sessions", register("fav4"), Map.of("mode", "FAVORITE"))).status())
                .isEqualTo(409);
        assertThat(List.of(q1, q2)).hasSize(2);
    }
}
