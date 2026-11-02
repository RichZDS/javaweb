package com.richzds.quiz.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.richzds.quiz.domain.FillOptions;
import com.richzds.quiz.entity.Category;
import com.richzds.quiz.entity.ExamPaper;
import com.richzds.quiz.entity.PaperQuestion;
import com.richzds.quiz.entity.PaperRule;
import com.richzds.quiz.entity.Question;
import com.richzds.quiz.exam.ExamSettlementJob;
import com.richzds.quiz.mapper.ExamPaperMapper;
import com.richzds.quiz.mapper.PaperQuestionMapper;
import com.richzds.quiz.mapper.PaperRuleMapper;
import com.richzds.quiz.support.ApiTestBase;

/** 模拟考试：开考、快照、版本、截止、交卷一次、定时结算、成绩单、考试中禁看题、规则卷。 */
class ExamApiTest extends ApiTestBase {

    @Autowired
    ExamPaperMapper paperMapper;
    @Autowired
    PaperQuestionMapper paperQuestionMapper;
    @Autowired
    PaperRuleMapper paperRuleMapper;
    @Autowired
    ExamSettlementJob settlementJob;

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private ExamPaper paper(Category exam, String mode, int durationMin, int total, int pass) {
        ExamPaper p = new ExamPaper().setTitle(mode + " 卷").setCategoryId(exam.getId()).setDescription("说明")
                .setMode(mode).setDurationMin(durationMin).setTotalScore(total).setPassScore(pass)
                .setStatus("ONLINE").setCreatedAt(now()).setUpdatedAt(now());
        paperMapper.insert(p);
        return p;
    }

    /** 固定卷：题目按给定顺序，每题 score 分。 */
    private ExamPaper fixedPaper(Category exam, List<Question> qs, int score, int durationMin, int pass) {
        ExamPaper p = paper(exam, "FIXED", durationMin, qs.size() * score, pass);
        for (int i = 0; i < qs.size(); i++) {
            paperQuestionMapper.insert(new PaperQuestion().setPaperId(p.getId()).setQuestionId(qs.get(i).getId())
                    .setScore(score).setSort(i));
        }
        return p;
    }

    private Resp start(Login u, ExamPaper p) throws Exception {
        return call(post("/api/exams/" + p.getId() + "/start", u, null));
    }

    private Resp save(Login u, long recordId, long questionId, List<String> answer, int version) throws Exception {
        return call(put("/api/exam-records/" + recordId + "/answers/" + questionId, u,
                Map.of("userAnswer", answer, "version", version, "marked", false)));
    }

    private JsonNode submit(Login u, long recordId) throws Exception {
        return data(call(post("/api/exam-records/" + recordId + "/submit", u, null)));
    }

    /** 考研卷：章节 c1 两道单选（答案 A）、章节 c2 一道判断（T）一道填空（int）。 */
    private record Fixture(Category exam, Category c1, Category c2, List<Question> qs, ExamPaper paper) {
    }

    private Fixture fixture() {
        Category exam = category(null, "408", 0);
        Category subject = category(exam, "数据结构", 0);
        Category c1 = category(subject, "线性表", 0);
        Category c2 = category(subject, "树", 1);
        Question s1 = single(c1, "A");
        Question s2 = single(c1, "A");
        Question j = judge(c2, true);
        Question f = fill(c2, new FillOptions(true, false), List.of(List.of("int")));
        List<Question> qs = List.of(s1, s2, j, f);
        return new Fixture(exam, c1, c2, qs, fixedPaper(exam, qs, 25, 60, 60));
    }

    @Test
    void startReturnsSheetWithoutAnswersAndIsReused() throws Exception {
        Login u = register("exam1");
        Fixture fx = fixture();
        Resp r = start(u, fx.paper());
        assertThat(r.status()).isEqualTo(201);
        String raw = r.body().toString();
        assertThat(raw).doesNotContain("correctAnswer").doesNotContain("acceptedAnswers")
                .doesNotContain("analysis").doesNotContain("解析");
        JsonNode sheet = r.data();
        assertThat(sheet.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(sheet.get("title").asText()).isEqualTo("FIXED 卷");
        assertThat(sheet.get("startedAt").asText()).isEqualTo("2026-11-02T08:00:00.000Z");
        assertThat(sheet.get("deadlineAt").asText()).isEqualTo("2026-11-02T09:00:00.000Z");
        assertThat(sheet.get("serverTime").asText()).isEqualTo("2026-11-02T08:00:00.000Z");
        assertThat(sheet.get("durationMin").asInt()).isEqualTo(60);
        assertThat(sheet.get("totalScore").asInt()).isEqualTo(100);
        assertThat(sheet.get("passScore").asInt()).isEqualTo(60);
        JsonNode qs = sheet.get("questions");
        assertThat(qs).hasSize(4);
        assertThat(qs.get(0).get("index").asInt()).isEqualTo(1);
        assertThat(ids(qs, "questionId")).containsExactlyElementsOf(fx.qs().stream().map(Question::getId).toList());
        assertThat(qs.get(0).get("score").asInt()).isEqualTo(25);
        assertThat(qs.get(0).get("options").get(0).get("html").asText()).isEqualTo("<p>选项 A</p>");
        assertThat(qs.get(2).get("options").get(0).get("key").asText()).isEqualTo("T");
        assertThat(qs.get(3).get("blankCount").asInt()).isEqualTo(1);
        assertThat(sheet.get("answers")).isEmpty();

        clock.advance(Duration.ofMinutes(5));
        Resp again = start(u, fx.paper());
        assertThat(again.status()).as("进行中的同卷考试直接返回").isEqualTo(200);
        assertThat(again.data().get("recordId").asLong()).isEqualTo(sheet.get("recordId").asLong());
        assertThat(again.data().get("serverTime").asText()).isEqualTo("2026-11-02T08:05:00.000Z");
    }

    @Test
    void answersCarryVersionsAndOldOnesAreRejected() throws Exception {
        Login u = register("exam2");
        Fixture fx = fixture();
        long rid = start(u, fx.paper()).data().get("recordId").asLong();
        long q = fx.qs().get(0).getId();

        JsonNode first = data(save(u, rid, q, List.of("B"), 1));
        assertThat(first.get("accepted").asBoolean()).isTrue();
        assertThat(first.get("version").asInt()).as("成功时返回本次版本").isEqualTo(1);
        JsonNode same = data(save(u, rid, q, List.of("C"), 1));
        assertThat(same.get("accepted").asBoolean()).isFalse();
        assertThat(same.get("version").asInt()).isEqualTo(1);
        JsonNode third = data(save(u, rid, q, List.of("A"), 3));
        assertThat(third.get("accepted").asBoolean()).isTrue();
        assertThat(third.get("version").asInt()).isEqualTo(3);
        JsonNode old = data(save(u, rid, q, List.of("D"), 2));
        assertThat(old.get("accepted").asBoolean()).as("旧版本到得晚也不覆盖").isFalse();
        assertThat(old.get("version").asInt()).isEqualTo(3);

        call(put("/api/exam-records/" + rid + "/answers/" + fx.qs().get(1).getId(), u,
                Map.of("userAnswer", List.of(), "version", 1, "marked", true)));
        JsonNode sheet = data(call(get("/api/exam-records/" + rid, u)));
        JsonNode answers = sheet.get("answers");
        assertThat(answers).hasSize(2);
        assertThat(answers.get(0).get("userAnswer").toString()).isEqualTo("[\"A\"]");
        assertThat(answers.get(0).get("version").asInt()).isEqualTo(3);
        assertThat(answers.get(1).get("marked").asBoolean()).isTrue();
        assertThat(answers.get(0).get("savedAt").asText()).isEqualTo("2026-11-02T08:00:00.000Z");

        Question outside = single(fx.c1(), "A");
        assertThat(save(u, rid, outside.getId(), List.of("A"), 1).status()).isEqualTo(404);
        assertThat(save(register("exam2b"), rid, q, List.of("A"), 9).status()).as("别人的考试").isEqualTo(404);
    }

    @Test
    void answersAfterDeadlineAreRejectedAndNotSaved() throws Exception {
        Login u = register("exam3");
        Fixture fx = fixture();
        long rid = start(u, fx.paper()).data().get("recordId").asLong();
        long q = fx.qs().get(0).getId();
        save(u, rid, q, List.of("B"), 1);

        clock.advance(Duration.ofMinutes(60));
        Resp late = save(u, rid, q, List.of("A"), 2);
        assertThat(late.status()).isEqualTo(409);
        assertThat(late.code()).isEqualTo("EXAM_EXPIRED");
        assertThat(jdbc.queryForObject("SELECT user_answer FROM exam_answer WHERE question_id = ?", String.class, q))
                .isEqualTo("[\"B\"]");
    }

    @Test
    void submitTwiceSettlesOnceAndFillsWrongBook() throws Exception {
        Login u = register("exam4");
        Fixture fx = fixture();
        List<Question> qs = fx.qs();
        long rid = start(u, fx.paper()).data().get("recordId").asLong();
        save(u, rid, qs.get(0).getId(), List.of("A"), 1);      // 对
        save(u, rid, qs.get(1).getId(), List.of("B"), 1);      // 错
        save(u, rid, qs.get(3).getId(), List.of(" INT "), 1);  // 对（忽略大小写）
        // 判断题不答
        clock.advance(Duration.ofMinutes(10));

        JsonNode result = submit(u, rid);
        assertThat(result.get("status").asText()).isEqualTo("FINISHED");
        assertThat(result.get("score").asInt()).isEqualTo(50);
        assertThat(result.get("passed").asBoolean()).isFalse();
        assertThat(result.get("submittedAt").asText()).isEqualTo("2026-11-02T08:10:00.000Z");
        assertThat(result.get("durationSec").asLong()).isEqualTo(600);
        assertThat(result.get("correctCount").asInt()).isEqualTo(2);
        assertThat(result.get("wrongCount").asInt()).isEqualTo(1);
        assertThat(result.get("unansweredCount").asInt()).isEqualTo(1);

        clock.advance(Duration.ofMinutes(1));
        JsonNode again = submit(u, rid);
        assertThat(again.get("score").asInt()).isEqualTo(50);
        assertThat(again.get("submittedAt").asText()).as("第二次交卷不改结果").isEqualTo("2026-11-02T08:10:00.000Z");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM answer_record WHERE source = 'EXAM' AND source_id = ?",
                Long.class, rid)).as("作答记录只写一次").isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM answer_record WHERE source = 'EXAM' AND answered = TRUE",
                Long.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT finish_reason FROM exam_record WHERE id = ?", String.class, rid))
                .isEqualTo("MANUAL");

        List<Long> wrong = jdbc.queryForList("SELECT question_id FROM wrong_question WHERE user_id = ?", Long.class,
                u.id());
        assertThat(wrong).as("答错的进错题本，没答的不进").containsExactly(qs.get(1).getId());

        Resp save = save(u, rid, qs.get(2).getId(), List.of("T"), 1);
        assertThat(save.status()).isEqualTo(409);
        assertThat(save.code()).isEqualTo("EXAM_EXPIRED");
    }

    @Test
    void resultHasBreakdownsAndItems() throws Exception {
        Login u = register("exam5");
        Fixture fx = fixture();
        List<Question> qs = fx.qs();
        long rid = start(u, fx.paper()).data().get("recordId").asLong();
        save(u, rid, qs.get(0).getId(), List.of("A"), 1);
        save(u, rid, qs.get(2).getId(), List.of("T"), 1);
        save(u, rid, qs.get(3).getId(), List.of("char"), 1);
        JsonNode r = submit(u, rid);

        JsonNode byType = r.get("byType");
        assertThat(byType.findValuesAsText("type")).containsExactly("SINGLE", "JUDGE", "FILL");
        assertThat(byType.get(0).get("score").asInt()).isEqualTo(25);
        assertThat(byType.get(0).get("total").asInt()).isEqualTo(50);
        assertThat(byType.get(0).get("rate").asDouble()).isEqualTo(0.5);
        JsonNode byCategory = r.get("byCategory");
        assertThat(byCategory).hasSize(2);
        assertThat(byCategory.get(0).get("categoryId").asLong()).isEqualTo(fx.c1().getId());
        assertThat(byCategory.get(0).get("categoryPath").asText()).isEqualTo("408 / 数据结构 / 线性表");
        assertThat(byCategory.get(1).get("score").asInt()).isEqualTo(25);
        assertThat(byCategory.get(1).get("total").asInt()).isEqualTo(50);

        JsonNode items = r.get("items");
        assertThat(items).hasSize(4);
        JsonNode second = items.get(1);
        assertThat(second.get("index").asInt()).isEqualTo(2);
        assertThat(second.get("userAnswer")).isEmpty();
        assertThat(second.get("correct").asBoolean()).isFalse();
        assertThat(second.get("score").asInt()).isZero();
        assertThat(second.get("maxScore").asInt()).isEqualTo(25);
        assertThat(second.get("correctAnswer").toString()).isEqualTo("[\"A\"]");
        assertThat(second.get("analysisHtml").asText()).contains("<strong>原因</strong>");
        assertThat(second.get("blankCount").asInt()).isZero();
        assertThat(second.has("imageUrl")).isTrue();
        assertThat(second.get("imageUrl").isNull()).isTrue();
        JsonNode fill = items.get(3);
        assertThat(fill.get("blankCount").asInt()).isEqualTo(1);
        assertThat(fill.get("correctAnswer").isNull()).isTrue();
        assertThat(fill.get("acceptedAnswers").toString()).isEqualTo("[[\"int\"]]");
        assertThat(fill.get("userAnswer").toString()).isEqualTo("[\"char\"]");
    }

    @Test
    void expiredExamIsSettledByTheJob() throws Exception {
        Login u = register("exam6");
        Fixture fx = fixture();
        long rid = start(u, fx.paper()).data().get("recordId").asLong();
        save(u, rid, fx.qs().get(0).getId(), List.of("A"), 1);

        clock.advance(Duration.ofMinutes(59));
        assertThat(settlementJob.run()).as("还没到截止时间").isZero();
        clock.advance(Duration.ofMinutes(1).plusSeconds(20));
        assertThat(settlementJob.run()).isEqualTo(1);
        assertThat(settlementJob.run()).as("已结算的不再结算").isZero();

        Map<String, Object> row = jdbc.queryForMap("SELECT status, finish_reason, score FROM exam_record WHERE id = ?", rid);
        assertThat(row.get("status")).isEqualTo("FINISHED");
        assertThat(row.get("finish_reason")).isEqualTo("TIMEOUT");
        assertThat(((Number) row.get("score")).intValue()).isEqualTo(25);
        JsonNode result = data(call(get("/api/exam-records/" + rid, u)));
        assertThat(result.get("submittedAt").asText()).as("超时交卷时间记为截止时间").isEqualTo("2026-11-02T09:00:00.000Z");
        assertThat(result.get("durationSec").asLong()).isEqualTo(3600);
    }

    @Test
    void expiredExamIsSettledWhenAccessed() throws Exception {
        Login u = register("exam7");
        Fixture fx = fixture();
        long rid = start(u, fx.paper()).data().get("recordId").asLong();
        clock.advance(Duration.ofMinutes(61));

        JsonNode r = data(call(get("/api/exam-records/" + rid, u)));
        assertThat(r.get("status").asText()).isEqualTo("FINISHED");
        assertThat(r.get("score").asInt()).isZero();
        assertThat(r.get("unansweredCount").asInt()).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wrong_question", Long.class)).isZero();

        Resp next = start(u, fx.paper());
        assertThat(next.status()).as("上一场已结束，开新的一场").isEqualTo(201);
        assertThat(next.data().get("recordId").asLong()).isNotEqualTo(rid);
    }

    @Test
    void editingQuestionsLaterDoesNotChangeOldResults() throws Exception {
        Login u = register("exam8");
        Fixture fx = fixture();
        Question q = fx.qs().get(0);
        long rid = start(u, fx.paper()).data().get("recordId").asLong();
        save(u, rid, q.getId(), List.of("A"), 1);
        submit(u, rid);

        q.setStem("改过的题干").setAnswerJson("[\"B\"]").setAnalysis("新解析").setVersion(2);
        questionMapper.updateById(q);
        JsonNode r = data(call(get("/api/exam-records/" + rid, u)));
        JsonNode item = r.get("items").get(0);
        assertThat(item.get("stemHtml").asText()).isEqualTo("<p>SINGLE 题干</p>");
        assertThat(item.get("correctAnswer").toString()).isEqualTo("[\"A\"]");
        assertThat(item.get("correct").asBoolean()).isTrue();
        assertThat(item.get("analysisHtml").asText()).doesNotContain("新解析");
        assertThat(r.get("score").asInt()).isEqualTo(25);
    }

    @Test
    void questionsOfARunningExamAreHidden() throws Exception {
        Login u = register("exam9");
        Fixture fx = fixture();
        long q = fx.qs().get(0).getId();
        long rid = start(u, fx.paper()).data().get("recordId").asLong();

        Resp hidden = call(get("/api/questions/" + q, u));
        assertThat(hidden.status()).isEqualTo(403);
        assertThat(hidden.code()).isEqualTo("EXAM_IN_PROGRESS");
        submit(u, rid);
        assertThat(call(get("/api/questions/" + q, u)).status()).isEqualTo(200);
    }

    @Test
    void rulePaperDrawsDistinctQuestionsOrReportsGaps() throws Exception {
        Login u = register("exam10");
        Category exam = category(null, "Java", 0);
        Category subject = category(exam, "基础", 0);
        Category c1 = category(subject, "语法", 0);
        Category c2 = category(subject, "集合", 1);
        singles(c1, 4);
        singles(c2, 4);
        for (int i = 0; i < 3; i++) {
            judge(c2, true);
        }
        ExamPaper p = paper(exam, "RULE", 30, 0, 10);
        // 整个考试抽 5 道单选，另外章节 c1 再抽 3 道单选：两条规则重叠但不能重复
        paperRuleMapper.insert(new PaperRule().setPaperId(p.getId()).setCategoryId(exam.getId()).setType("SINGLE")
                .setCount(5).setScore(2).setSort(0));
        paperRuleMapper.insert(new PaperRule().setPaperId(p.getId()).setCategoryId(c1.getId()).setType("SINGLE")
                .setCount(3).setScore(3).setSort(1));
        paperRuleMapper.insert(new PaperRule().setPaperId(p.getId()).setCategoryId(c2.getId()).setType("JUDGE")
                .setCount(2).setScore(5).setSort(2));

        JsonNode sheet = data(start(u, p));
        JsonNode qs = sheet.get("questions");
        assertThat(qs).hasSize(10);
        assertThat(new HashSet<>(ids(qs, "questionId"))).as("不重复").hasSize(10);
        assertThat(sheet.get("totalScore").asInt()).isEqualTo(5 * 2 + 3 * 3 + 2 * 5);
        assertThat(qs.get(5).get("score").asInt()).isEqualTo(3);
        assertThat(qs.get(9).get("type").asText()).isEqualTo("JUDGE");
        submit(u, sheet.get("recordId").asLong());

        PaperRule more = new PaperRule().setPaperId(p.getId()).setCategoryId(c2.getId()).setType("JUDGE")
                .setCount(2).setScore(1).setSort(3);
        paperRuleMapper.insert(more);
        Resp gap = start(u, p);
        assertThat(gap.status()).isEqualTo(409);
        assertThat(gap.code()).isEqualTo("POOL_INSUFFICIENT");
        JsonNode g = gap.data().get("gaps").get(0);
        assertThat(g.get("categoryId").asLong()).isEqualTo(c2.getId());
        assertThat(g.get("categoryPath").asText()).isEqualTo("Java / 基础 / 集合");
        assertThat(g.get("type").asText()).isEqualTo("JUDGE");
        assertThat(g.get("need").asInt()).isEqualTo(4);
        assertThat(g.get("available").asInt()).isEqualTo(3);
    }

    @Test
    void paperListDetailAndRecords() throws Exception {
        Login u = register("exam11");
        Fixture fx = fixture();
        Category other = category(null, "其他考试", 1);
        Question oq = single(category(category(other, "s", 0), "c", 0), "A");
        ExamPaper otherPaper = fixedPaper(other, List.of(oq), 10, 10, 5);
        ExamPaper offline = fixedPaper(fx.exam(), fx.qs(), 1, 10, 1);
        offline.setStatus("OFFLINE");
        paperMapper.updateById(offline);
        setTarget(u, fx.exam());

        JsonNode list = data(call(get("/api/exams", u)));
        assertThat(ids(list, "paperId")).as("默认按目标考试、只列上架的").containsExactly(fx.paper().getId());
        JsonNode item = list.get(0);
        assertThat(item.get("questionCount").asInt()).isEqualTo(4);
        assertThat(item.get("myAttempts").asInt()).isZero();
        assertThat(item.get("myBestScore").isNull()).isTrue();
        assertThat(item.get("inProgressRecordId").isNull()).isTrue();
        assertThat(ids(data(call(get("/api/exams?categoryId=" + other.getId(), u))), "paperId"))
                .containsExactly(otherPaper.getId());

        long r1 = start(u, fx.paper()).data().get("recordId").asLong();
        assertThat(data(call(get("/api/exams", u))).get(0).get("inProgressRecordId").asLong()).isEqualTo(r1);
        save(u, r1, fx.qs().get(0).getId(), List.of("A"), 1);
        submit(u, r1);
        long r2 = start(u, fx.paper()).data().get("recordId").asLong();
        submit(u, r2);

        JsonNode detail = data(call(get("/api/exams/" + fx.paper().getId(), u)));
        assertThat(detail.get("myAttempts").asInt()).isEqualTo(2);
        assertThat(detail.get("myBestScore").asInt()).isEqualTo(25);
        assertThat(detail.get("description").asText()).isEqualTo("说明");
        assertThat(detail.get("scoringRules").asText()).contains("多选题");
        assertThat(call(get("/api/exams/" + offline.getId(), u)).status()).isEqualTo(404);
        assertThat(call(post("/api/exams/" + offline.getId() + "/start", u, null)).status()).isEqualTo(404);

        JsonNode records = data(call(get("/api/exam-records?paperId=" + fx.paper().getId(), u)));
        assertThat(records.get("total").asLong()).isEqualTo(2);
        JsonNode latest = records.get("items").get(0);
        assertThat(latest.get("recordId").asLong()).isEqualTo(r2);
        assertThat(latest.get("title").asText()).isEqualTo("FIXED 卷");
        assertThat(latest.get("status").asText()).isEqualTo("FINISHED");
        assertThat(latest.get("totalScore").asInt()).isEqualTo(100);
        assertThat(latest.get("passed").asBoolean()).isFalse();
        assertThat(call(get("/api/exam-records/" + r1, register("exam11b"))).status()).isEqualTo(404);
    }

    @Test
    void retryFromExamUsesAnsweredWrongOnesInPaperOrder() throws Exception {
        Login u = register("exam12");
        Fixture fx = fixture();
        List<Question> qs = fx.qs();
        long rid = start(u, fx.paper()).data().get("recordId").asLong();
        Map<String, Object> body = new HashMap<>(Map.of("mode", "RETRY", "fromExamRecordId", rid));
        assertThat(call(post("/api/practice/sessions", u, body)).status()).as("还没结算").isEqualTo(409);

        save(u, rid, qs.get(3).getId(), List.of("char"), 1);
        save(u, rid, qs.get(1).getId(), List.of("B"), 1);
        save(u, rid, qs.get(0).getId(), List.of("A"), 1);
        submit(u, rid);

        Resp retry = call(post("/api/practice/sessions", u, body));
        assertThat(retry.status()).isEqualTo(201);
        assertThat(ids(retry.data().get("questions"), "id")).as("按试卷顺序，没答的不算")
                .containsExactly(qs.get(1).getId(), qs.get(3).getId());
    }

    /** 考试记录的范围（docs/api.md 0.4 节、第 6 节）：默认目标考试、指定一级分类、all=true、非一级分类 400、paperId 优先。 */
    @Test
    void recordsAreScopedToTheTargetExam() throws Exception {
        Login u = register("rec_scope");
        Category[] a = tree("EA");
        Category[] b = tree("EB");
        ExamPaper pa = fixedPaper(a[0], singles(a[2], 2), 10, 30, 10);
        ExamPaper pa2 = fixedPaper(a[0], singles(a[2], 2), 10, 30, 10);
        ExamPaper pb = fixedPaper(b[0], singles(b[2], 2), 10, 30, 10);
        long ra = 0;
        long ra2 = 0;
        long rb = 0;
        for (ExamPaper p : List.of(pa, pb, pa2)) {
            long rid = data(start(u, p)).get("recordId").asLong();
            call(post("/api/exam-records/" + rid + "/submit", u, null));
            clock.advance(Duration.ofMinutes(1));
            if (p == pa) {
                ra = rid;
            } else if (p == pb) {
                rb = rid;
            } else {
                ra2 = rid;
            }
        }
        String list = "/api/exam-records";

        assertThat(ids(data(call(get(list, u))).get("items"), "recordId")).as("没有目标考试：全部")
                .containsExactly(ra2, rb, ra);
        setTarget(u, a[0]);
        JsonNode mine = data(call(get(list, u)));
        assertThat(ids(mine.get("items"), "recordId")).as("默认只看目标考试 EA").containsExactly(ra2, ra);
        assertThat(mine.get("total").asLong()).isEqualTo(2);
        assertThat(mine.get("items").get(0).get("rootCategoryId").asLong()).isEqualTo(a[0].getId());
        assertThat(mine.get("items").get(0).get("rootCategoryName").asText()).isEqualTo("EA");
        JsonNode inB = data(call(get(list + "?categoryId=" + b[0].getId(), u)));
        assertThat(ids(inB.get("items"), "recordId")).as("指定一级分类").containsExactly(rb);
        assertThat(inB.get("items").get(0).get("rootCategoryName").asText()).isEqualTo("EB");
        assertThat(ids(data(call(get(list + "?all=true&categoryId=" + a[2].getId(), u))).get("items"), "recordId"))
                .as("all=true 列全部、忽略 categoryId").containsExactly(ra2, rb, ra);
        assertThat(ids(data(call(get(list + "?paperId=" + pb.getId() + "&categoryId=" + a[2].getId() + "&all=false", u)))
                .get("items"), "recordId")).as("paperId 优先：不看目标考试、忽略 categoryId 和 all").containsExactly(rb);
        assertThat(ids(data(call(get(list + "?paperId=" + pa.getId(), u))).get("items"), "recordId"))
                .containsExactly(ra);
        Category[] empty = tree("EC");
        assertThat(data(call(get(list + "?categoryId=" + empty[0].getId(), u))).get("total").asLong())
                .as("这个考试下没有试卷").isZero();

        for (String bad : List.of(String.valueOf(a[2].getId()), String.valueOf(a[1].getId()), "999999")) {
            Resp r = call(get(list + "?categoryId=" + bad, u));
            assertThat(r.status()).as("categoryId=" + bad).isEqualTo(400);
            assertThat(r.code()).isEqualTo("VALIDATION_FAILED");
            assertThat(r.body().get("data").get("fields").get(0).get("field").asText()).isEqualTo("categoryId");
        }
    }
}
