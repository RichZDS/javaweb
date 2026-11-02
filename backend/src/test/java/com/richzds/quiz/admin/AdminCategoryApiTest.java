package com.richzds.quiz.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.richzds.quiz.entity.Category;
import com.richzds.quiz.entity.ExamPaper;
import com.richzds.quiz.mapper.ExamPaperMapper;
import com.richzds.quiz.support.ApiTestBase;

/** 后台分类管理。 */
class AdminCategoryApiTest extends ApiTestBase {

    @Autowired
    ExamPaperMapper paperMapper;

    @Test
    void treeCountsDraftAndPublishedButNotArchived() throws Exception {
        Login admin = admin();
        Category[] t = tree("408");
        single(t[2], "A");
        choice(t[2], "SINGLE", 4, List.of("A"), "DRAFT");
        choice(t[2], "SINGLE", 4, List.of("A"), "ARCHIVED");

        JsonNode roots = data(call(get("/api/admin/categories", admin)));
        assertThat(roots).hasSize(1);
        JsonNode root = roots.get(0);
        assertThat(root.get("questionCount").asLong()).isEqualTo(2);
        JsonNode chapter = root.get("children").get(0).get("children").get(0);
        assertThat(chapter.get("level").asInt()).isEqualTo(3);
        assertThat(chapter.get("questionCount").asLong()).isEqualTo(2);
        assertThat(chapter.get("children")).isEmpty();
    }

    @Test
    void createUpdateAndLevels() throws Exception {
        Login admin = admin();
        Resp exam = call(post("/api/admin/categories", admin, Map.of("name", "  Java  ", "sort", 2)));
        assertThat(exam.status()).isEqualTo(201);
        assertThat(exam.data().get("level").asInt()).isEqualTo(1);
        assertThat(exam.data().get("name").asText()).isEqualTo("Java");
        assertThat(exam.data().get("parentId").isNull()).isTrue();
        assertThat(exam.data().get("children")).isEmpty();
        long examId = exam.data().get("id").asLong();

        long subject = data(call(post("/api/admin/categories", admin, Map.of("parentId", examId, "name", "基础"))))
                .get("id").asLong();
        JsonNode chapter = data(call(post("/api/admin/categories", admin, Map.of("parentId", subject, "name", "语法"))));
        assertThat(chapter.get("level").asInt()).isEqualTo(3);
        assertThat(chapter.get("sort").asInt()).isZero();

        Resp tooDeep = call(post("/api/admin/categories", admin,
                Map.of("parentId", chapter.get("id").asLong(), "name", "第四级")));
        assertThat(tooDeep.status()).isEqualTo(400);
        assertThat(tooDeep.data().get("fields").get(0).get("field").asText()).isEqualTo("parentId");
        assertThat(call(post("/api/admin/categories", admin, Map.of("parentId", 999999, "name", "x"))).status())
                .isEqualTo(400);
        assertThat(call(post("/api/admin/categories", admin, Map.of("name", " "))).status()).isEqualTo(400);

        JsonNode renamed = data(call(put("/api/admin/categories/" + subject, admin, Map.of("name", "基础语法", "sort", 5))));
        assertThat(renamed.get("name").asText()).isEqualTo("基础语法");
        assertThat(renamed.get("sort").asInt()).isEqualTo(5);
        assertThat(renamed.get("level").asInt()).isEqualTo(2);
        assertThat(call(put("/api/admin/categories/999999", admin, Map.of("name", "x"))).status()).isEqualTo(404);
    }

    @Test
    void deleteIsBlockedByChildrenQuestionsAndPapers() throws Exception {
        Login admin = admin();
        Category[] t = tree("408");
        Login student = register("cat_student");
        setTarget(student, t[0]);

        Resp hasChildren = call(delete("/api/admin/categories/" + t[1].getId(), admin));
        assertThat(hasChildren.status()).isEqualTo(409);
        assertThat(hasChildren.code()).isEqualTo("CONFLICT");

        choice(t[2], "SINGLE", 4, List.of("A"), "ARCHIVED");
        assertThat(call(delete("/api/admin/categories/" + t[2].getId(), admin)).status())
                .as("已归档的题也算").isEqualTo(409);
        jdbc.update("DELETE FROM question");
        assertThat(call(delete("/api/admin/categories/" + t[2].getId(), admin)).status()).isEqualTo(200);
        assertThat(call(delete("/api/admin/categories/" + t[1].getId(), admin)).status()).isEqualTo(200);

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        ExamPaper p = new ExamPaper().setTitle("卷").setCategoryId(t[0].getId()).setMode("FIXED").setDurationMin(10)
                .setTotalScore(0).setPassScore(0).setStatus("OFFLINE").setCreatedAt(now).setUpdatedAt(now);
        paperMapper.insert(p);
        assertThat(call(delete("/api/admin/categories/" + t[0].getId(), admin)).status()).isEqualTo(409);
        paperMapper.deleteById(p.getId());

        Resp ok = call(delete("/api/admin/categories/" + t[0].getId(), admin));
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.data().isNull()).isTrue();
        assertThat(data(call(get("/api/me", student))).get("targetCategoryId").isNull())
                .as("删掉的考试从目标里清掉").isTrue();
        assertThat(call(delete("/api/admin/categories/" + t[0].getId(), admin)).status()).isEqualTo(404);
    }
}
