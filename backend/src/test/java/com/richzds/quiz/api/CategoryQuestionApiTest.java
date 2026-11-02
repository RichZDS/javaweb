package com.richzds.quiz.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import com.richzds.quiz.common.JsonUtil;
import com.richzds.quiz.domain.FillOptions;
import com.richzds.quiz.entity.Category;
import com.richzds.quiz.entity.ExamRecord;
import com.richzds.quiz.entity.Question;
import com.richzds.quiz.exam.ExamSnapshot;
import com.richzds.quiz.mapper.ExamRecordMapper;
import com.richzds.quiz.support.ApiTestBase;

import org.springframework.beans.factory.annotation.Autowired;

/** 分类树、搜题、题目详情（含考试中禁看）和纠错。 */
class CategoryQuestionApiTest extends ApiTestBase {

    @Autowired
    ExamRecordMapper examRecordMapper;

    @Test
    void rootsOnlyWithoutRootId() throws Exception {
        Login u = register("user1");
        Category[] a = tree("考研 408");
        category(null, "Java 程序设计", 1);
        single(a[2], "A");

        JsonNode roots = data(call(get("/api/categories", u)));
        assertThat(roots).hasSize(2);
        assertThat(roots.get(0).get("name").asText()).isEqualTo("考研 408");
        assertThat(roots.get(0).get("level").asInt()).isEqualTo(1);
        assertThat(roots.get(0).get("questionCount").asLong()).isEqualTo(1);
        assertThat(roots.get(0).get("children")).isEmpty();
        assertThat(roots.get(0).get("correctRate").isNull()).isTrue();
    }

    @Test
    void treeWithProgress() throws Exception {
        Login u = register("user2");
        Category exam = category(null, "考研 408", 0);
        Category ds = category(exam, "数据结构", 0);
        Category linear = category(ds, "线性表", 0);
        Category tree = category(ds, "树", 1);
        Question q1 = single(linear, "A");
        Question q2 = single(linear, "B");
        single(tree, "A");
        choice(tree, "SINGLE", 4, List.of("A"), "DRAFT"); // 草稿不计数

        // 做两题：q1 对、q2 错；再把 q1 在另一次练习里做错
        long s1 = data(call(post("/api/practice/sessions", u, Map.of("mode", "SEQUENTIAL",
                "categoryIds", List.of(linear.getId()))))).get("id").asLong();
        call(post("/api/practice/sessions/" + s1 + "/answers", u, Map.of("questionId", q1.getId(), "userAnswer", List.of("A"))));
        call(post("/api/practice/sessions/" + s1 + "/answers", u, Map.of("questionId", q2.getId(), "userAnswer", List.of("A"))));
        long s2 = data(call(post("/api/practice/sessions", u, Map.of("mode", "SEQUENTIAL",
                "categoryIds", List.of(linear.getId()))))).get("id").asLong();
        call(post("/api/practice/sessions/" + s2 + "/answers", u, Map.of("questionId", q1.getId(), "userAnswer", List.of("C"))));

        JsonNode root = data(call(get("/api/categories?rootId=" + exam.getId(), u)));
        assertThat(root.isObject()).as("传 rootId 时 data 是根节点对象").isTrue();
        assertThat(root.get("id").asLong()).isEqualTo(exam.getId());
        assertThat(root.get("questionCount").asLong()).isEqualTo(3);
        assertThat(root.get("doneCount").asLong()).isEqualTo(2);
        assertThat(root.get("correctRate").asDouble()).isEqualTo(0.3333);
        JsonNode subject = root.get("children").get(0);
        assertThat(subject.get("parentId").asLong()).isEqualTo(exam.getId());
        JsonNode chapters = subject.get("children");
        assertThat(chapters).hasSize(2);
        assertThat(chapters.get(0).get("name").asText()).isEqualTo("线性表");
        assertThat(chapters.get(0).get("doneCount").asLong()).isEqualTo(2);
        assertThat(chapters.get(1).get("questionCount").asLong()).isEqualTo(1);
        assertThat(chapters.get(1).get("doneCount").asLong()).isZero();
        assertThat(chapters.get(1).get("correctRate").isNull()).isTrue();

        // 进度只算本人的
        Login other = register("user2b");
        JsonNode otherRoot = data(call(get("/api/categories?rootId=" + exam.getId(), other)));
        assertThat(otherRoot.get("doneCount").asLong()).isZero();

        assertThat(call(get("/api/categories?rootId=" + ds.getId(), u)).status()).isEqualTo(404);
    }

    @Test
    void searchByKeywordAndCategory() throws Exception {
        Login u = register("user3");
        Category[] a = tree("A");
        Category[] b = tree("B");
        Question hit = save(base(a[2], "JUDGE", "**链表**的插入 `O(1)` 吗？").setAnswerJson("[\"T\"]"));
        save(base(b[2], "JUDGE", "链表的删除").setAnswerJson("[\"T\"]"));
        save(base(a[2], "JUDGE", "链表草稿").setAnswerJson("[\"T\"]").setStatus("DRAFT"));
        save(base(a[2], "JUDGE", "百分号 100% 的题").setAnswerJson("[\"T\"]"));

        JsonNode all = data(call(get("/api/questions/search?keyword=链表", u)));
        assertThat(all.get("total").asLong()).isEqualTo(2);
        assertThat(all.get("page").asLong()).isEqualTo(1);
        assertThat(all.get("size").asLong()).isEqualTo(20);

        JsonNode inA = data(call(get("/api/questions/search?keyword=链表&categoryId=" + a[0].getId(), u)));
        assertThat(inA.get("total").asLong()).isEqualTo(1);
        JsonNode item = inA.get("items").get(0);
        assertThat(item.get("id").asLong()).isEqualTo(hit.getId());
        assertThat(item.get("categoryPath").asText()).isEqualTo("A / A-科目 / A-章节");
        assertThat(item.get("type").asText()).isEqualTo("JUDGE");
        assertThat(item.get("stemPreview").asText()).isEqualTo("链表的插入 O(1) 吗？");

        // LIKE 通配符被转义：搜 "%" 只命中真的含百分号的题
        assertThat(data(call(get("/api/questions/search", u).param("keyword", "%"))).get("total").asLong()).isEqualTo(1);
        assertThat(data(call(get("/api/questions/search", u).param("keyword", "_"))).get("total").asLong()).isZero();
        assertThat(data(call(get("/api/questions/search?keyword=链表&size=1&page=2", u))).get("items")).hasSize(1);
    }

    @Test
    void detailIncludesAnswerAndAnalysis() throws Exception {
        Login u = register("user4");
        Category[] t = tree("T");
        Question m = multiple(t[2], "A", "C");
        Question f = fill(t[2], new FillOptions(true, false), List.of(List.of("int", "Integer")));
        Question j = judge(t[2], true);

        JsonNode dm = data(call(get("/api/questions/" + m.getId(), u)));
        assertThat(dm.get("type").asText()).isEqualTo("MULTIPLE");
        assertThat(dm.get("options")).hasSize(5);
        assertThat(dm.get("options").get(0).get("key").asText()).isEqualTo("A");
        assertThat(dm.get("options").get(0).get("html").asText()).isEqualTo("<p>选项 A</p>");
        assertThat(dm.get("stemHtml").asText()).startsWith("<p>");
        assertThat(dm.get("correctAnswer").toString()).isEqualTo("[\"A\",\"C\"]");
        assertThat(dm.get("acceptedAnswers").isNull()).isTrue();
        assertThat(dm.get("analysisHtml").asText()).isEqualTo("<p>解析：<strong>原因</strong></p>");
        assertThat(dm.get("favorited").asBoolean()).isFalse();
        assertThat(dm.get("blankCount").asInt()).isZero();

        JsonNode df = data(call(get("/api/questions/" + f.getId(), u)));
        assertThat(df.get("options")).isEmpty();
        assertThat(df.get("blankCount").asInt()).isEqualTo(1);
        assertThat(df.get("correctAnswer").isNull()).isTrue();
        assertThat(df.get("acceptedAnswers").toString()).isEqualTo("[[\"int\",\"Integer\"]]");
        assertThat(df.get("fillOptions").get("ignoreCase").asBoolean()).isTrue();

        JsonNode dj = data(call(get("/api/questions/" + j.getId(), u)));
        assertThat(dj.get("options").toString()).contains("\"key\":\"T\",\"text\":\"正确\"")
                .contains("\"key\":\"F\",\"text\":\"错误\"");

        Question draft = choice(t[2], "SINGLE", 4, List.of("A"), "DRAFT");
        assertThat(call(get("/api/questions/" + draft.getId(), u)).status()).isEqualTo(404);
        assertThat(call(get("/api/questions/999999", u)).status()).isEqualTo(404);
    }

    @Test
    void questionInRunningExamIsForbiddenUntilDeadline() throws Exception {
        Login u = register("user5");
        Category[] t = tree("T");
        Question q = single(t[2], "A");
        Question other = single(t[2], "B");
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        ExamSnapshot snapshot = new ExamSnapshot("卷", List.of(new ExamSnapshot.Item(1, q.getId(), t[2].getId(), "",
                "SINGLE", "x", List.of(), 0, null, 2, List.of("A"), null, null, null)));
        examRecordMapper.insert(new ExamRecord().setUserId(u.id()).setPaperId(1L)
                .setQuestionSnapshot(JsonUtil.write(snapshot)).setTotalScore(2).setPassScore(1)
                .setStartedAt(now).setDeadlineAt(now.plusMinutes(30)).setStatus("IN_PROGRESS"));

        Resp r = call(get("/api/questions/" + q.getId(), u));
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.code()).isEqualTo("EXAM_IN_PROGRESS");
        assertThat(call(get("/api/questions/" + other.getId(), u)).status()).isEqualTo(200);
        assertThat(call(get("/api/questions/" + q.getId(), register("user5b"))).status())
                .as("只限制正在考试的本人").isEqualTo(200);

        clock.advance(Duration.ofMinutes(31));
        assertThat(call(get("/api/questions/" + q.getId(), u)).status()).isEqualTo(200);
    }

    @Test
    void feedback() throws Exception {
        Login u = register("user6");
        Category[] t = tree("T");
        Question q = single(t[2], "A");

        Resp ok = call(post("/api/questions/" + q.getId() + "/feedback", u,
                Map.of("type", "ANSWER_WRONG", "content", "答案应该是 B")));
        assertThat(ok.status()).isEqualTo(201);
        assertThat(ok.data().get("status").asText()).isEqualTo("PENDING");
        assertThat(ok.data().get("questionId").asLong()).isEqualTo(q.getId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM question_feedback WHERE user_id = ?", Long.class, u.id()))
                .isEqualTo(1);

        assertThat(call(post("/api/questions/" + q.getId() + "/feedback", u,
                Map.of("type", "ANSWER_WRONG", "content", " "))).status()).isEqualTo(400);
        assertThat(call(post("/api/questions/" + q.getId() + "/feedback", u,
                Map.of("type", "ANSWER_WRONG", "content", "x".repeat(501)))).status()).isEqualTo(400);
        assertThat(call(post("/api/questions/" + q.getId() + "/feedback", u,
                Map.of("type", "BAD_TYPE", "content", "x"))).status()).isEqualTo(400);
    }
}
