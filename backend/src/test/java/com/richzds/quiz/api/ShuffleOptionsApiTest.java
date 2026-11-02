package com.richzds.quiz.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import com.richzds.quiz.entity.Category;
import com.richzds.quiz.entity.Question;
import com.richzds.quiz.support.ApiTestBase;

/** 选项打乱（docs/api.md 第 4、6 节）：新标签返回和提交，库里存原标签，判分映射回原选项。 */
class ShuffleOptionsApiTest extends ApiTestBase {

    /** 新标签 → 原标签。 */
    private static Map<String, String> mapping(JsonNode q) {
        Map<String, String> m = new HashMap<>();
        q.get("optionMapping").fields().forEachRemaining(e -> m.put(e.getKey(), e.getValue().asText()));
        return m;
    }

    /** 原标签 orig 现在显示成哪个新标签。 */
    private static String displayOf(Map<String, String> mapping, String orig) {
        return mapping.entrySet().stream().filter(e -> e.getValue().equals(orig)).findFirst().orElseThrow().getKey();
    }

    private static JsonNode find(JsonNode questions, long id, String idField) {
        for (JsonNode q : questions) {
            if (q.get(idField).asLong() == id) {
                return q;
            }
        }
        throw new AssertionError("找不到题 " + id);
    }

    /** 选项内容和映射对得上：新标签 X 的内容就是原选项 mapping[X] 的内容（夹具里原选项内容是“选项 原标签”）。 */
    private static void assertOptionsFollowMapping(JsonNode q) {
        Map<String, String> m = mapping(q);
        JsonNode options = q.get("options");
        assertThat(m).hasSize(options.size());
        for (int i = 0; i < options.size(); i++) {
            String key = options.get(i).get("key").asText();
            assertThat(key).isEqualTo(String.valueOf((char) ('A' + i)));
            assertThat(options.get(i).get("text").asText()).isEqualTo("选项 " + m.get(key));
        }
    }

    @Test
    void practiceShowsNewLabelsAndStoresOriginalOnes() throws Exception {
        Login u = register("shuffle1");
        Category[] t = tree("T");
        Question single = single(t[2], "B");
        Question multi = multiple(t[2], "A", "C");
        Question j = judge(t[2], true);

        Resp started = call(post("/api/practice/sessions", u, Map.of("mode", "SEQUENTIAL",
                "categoryIds", List.of(t[2].getId()), "shuffleOptions", true)));
        assertThat(started.status()).isEqualTo(201);
        JsonNode s = started.data();
        assertThat(s.get("shuffleOptions").asBoolean()).isTrue();
        long sid = s.get("id").asLong();
        JsonNode qs = s.get("questions");
        JsonNode qSingle = find(qs, single.getId(), "id");
        JsonNode qMulti = find(qs, multi.getId(), "id");
        assertOptionsFollowMapping(qSingle);
        assertOptionsFollowMapping(qMulti);
        assertThat(find(qs, j.getId(), "id").get("optionMapping").isNull()).as("判断题不打乱").isTrue();
        assertThat(find(qs, j.getId(), "id").get("options").get(0).get("key").asText()).isEqualTo("T");

        // 同一次练习里顺序固定
        JsonNode again = data(call(get("/api/practice/sessions/" + sid, u)));
        assertThat(find(again.get("questions"), single.getId(), "id").get("optionMapping"))
                .isEqualTo(qSingle.get("optionMapping"));

        // 单选：按新标签提交正确答案
        Map<String, String> ms = mapping(qSingle);
        String correctDisplay = displayOf(ms, "B");
        JsonNode r1 = data(call(post("/api/practice/sessions/" + sid + "/answers", u,
                Map.of("questionId", single.getId(), "userAnswer", List.of(correctDisplay)))));
        assertThat(r1.get("correct").asBoolean()).isTrue();
        assertThat(r1.get("userAnswer").toString()).isEqualTo("[\"" + correctDisplay + "\"]");
        assertThat(r1.get("correctAnswer").toString()).isEqualTo("[\"" + correctDisplay + "\"]");
        assertThat(r1.get("optionMapping")).isEqualTo(qSingle.get("optionMapping"));
        assertThat(jdbc.queryForObject("SELECT user_answer FROM answer_record WHERE question_id = ?", String.class,
                single.getId())).as("库里存原标签").isEqualTo("[\"B\"]");

        // 多选：只选了原 A 对应的新标签（少选），判错；错题本记原标签
        Map<String, String> mm = mapping(qMulti);
        JsonNode r2 = data(call(post("/api/practice/sessions/" + sid + "/answers", u,
                Map.of("questionId", multi.getId(), "userAnswer", List.of(displayOf(mm, "A"))))));
        assertThat(r2.get("correct").asBoolean()).isFalse();
        List<String> expected = new ArrayList<>(List.of(displayOf(mm, "A"), displayOf(mm, "C")));
        expected.sort(null);
        assertThat(r2.get("correctAnswer").toString()).isEqualTo("[\"" + String.join("\",\"", expected) + "\"]");
        assertThat(jdbc.queryForObject("SELECT last_wrong_answer FROM wrong_question WHERE question_id = ?",
                String.class, multi.getId())).isEqualTo("[\"A\"]");
        JsonNode wrongList = data(call(get("/api/wrong-questions", u)));
        assertThat(wrongList.get("items").get(0).get("lastWrongAnswer").toString()).isEqualTo("[\"A\"]");

        // 回看：已提交的题 userAnswer、correctAnswer 都是新标签
        JsonNode view = find(data(call(get("/api/practice/sessions/" + sid, u))).get("questions"), multi.getId(), "id");
        assertThat(view.get("userAnswer").toString()).isEqualTo("[\"" + displayOf(mm, "A") + "\"]");
        assertThat(view.get("correctAnswer").toString()).isEqualTo(r2.get("correctAnswer").toString());

        // 复用的顺序练习沿用当初的设置
        JsonNode reused = data(call(post("/api/practice/sessions", u, Map.of("mode", "SEQUENTIAL",
                "categoryIds", List.of(t[2].getId()), "shuffleOptions", false))));
        assertThat(reused.get("id").asLong()).isEqualTo(sid);
        assertThat(reused.get("shuffleOptions").asBoolean()).isTrue();
    }

    @Test
    void originalLetterIsJudgedAgainstTheNewOrder() throws Exception {
        Login u = register("shuffle2");
        Category[] t = tree("T");
        Question single = single(t[2], "A");
        JsonNode s = data(call(post("/api/practice/sessions", u, Map.of("mode", "SEQUENTIAL",
                "categoryIds", List.of(t[2].getId()), "shuffleOptions", true))));
        Map<String, String> m = mapping(s.get("questions").get(0));
        JsonNode r = data(call(post("/api/practice/sessions/" + s.get("id").asLong() + "/answers", u,
                Map.of("questionId", single.getId(), "userAnswer", List.of("A")))));
        assertThat(r.get("correct").asBoolean()).as("提交的 A 是新标签，指向原 " + m.get("A"))
                .isEqualTo("A".equals(m.get("A")));
    }

    @Test
    void notShuffledByDefault() throws Exception {
        Login u = register("shuffle3");
        Category[] t = tree("T");
        single(t[2], "A");
        JsonNode s = data(call(post("/api/practice/sessions", u, Map.of("mode", "SEQUENTIAL",
                "categoryIds", List.of(t[2].getId())))));
        assertThat(s.get("shuffleOptions").asBoolean()).isFalse();
        JsonNode q = s.get("questions").get(0);
        assertThat(q.has("optionMapping")).isTrue();
        assertThat(q.get("optionMapping").isNull()).isTrue();
        assertThat(q.get("options").get(1).get("text").asText()).isEqualTo("选项 B");
    }

    @Test
    void examsShuffleBySnapshotAndKeepItAfterThePaperChanges() throws Exception {
        Login admin = admin();
        Category[] t = tree("408");
        Question single = single(t[2], "C");
        Question multi = multiple(t[2], "B", "D");
        Question j = judge(t[2], false);
        Map<String, Object> paper = new HashMap<>();
        paper.put("title", "打乱卷");
        paper.put("categoryId", t[0].getId());
        paper.put("mode", "FIXED");
        paper.put("durationMin", 30);
        paper.put("passScore", 10);
        paper.put("shuffleOptions", true);
        paper.put("questions", List.of(Map.of("questionId", single.getId(), "score", 10),
                Map.of("questionId", multi.getId(), "score", 10), Map.of("questionId", j.getId(), "score", 10)));
        JsonNode created = data(call(post("/api/admin/papers", admin, paper)));
        assertThat(created.get("shuffleOptions").asBoolean()).isTrue();
        long pid = created.get("id").asLong();
        call(put("/api/admin/papers/" + pid + "/status", admin, Map.of("status", "ONLINE")));

        Login u = register("shuffle_exam");
        assertThat(data(call(get("/api/exams/" + pid, u))).get("shuffleOptions").asBoolean()).isTrue();
        JsonNode sheet = data(call(post("/api/exams/" + pid + "/start", u, null)));
        assertThat(sheet.get("shuffleOptions").asBoolean()).isTrue();
        long rid = sheet.get("recordId").asLong();
        JsonNode sSingle = find(sheet.get("questions"), single.getId(), "questionId");
        JsonNode sMulti = find(sheet.get("questions"), multi.getId(), "questionId");
        assertOptionsFollowMapping(sSingle);
        assertOptionsFollowMapping(sMulti);
        assertThat(find(sheet.get("questions"), j.getId(), "questionId").get("optionMapping").isNull()).isTrue();

        Map<String, String> ms = mapping(sSingle);
        Map<String, String> mm = mapping(sMulti);
        String singleAnswer = displayOf(ms, "C");
        call(put("/api/exam-records/" + rid + "/answers/" + single.getId(), u,
                Map.of("userAnswer", List.of(singleAnswer), "version", 1)));
        call(put("/api/exam-records/" + rid + "/answers/" + multi.getId(), u,
                Map.of("userAnswer", List.of(displayOf(mm, "B"), displayOf(mm, "D")), "version", 1)));
        assertThat(jdbc.queryForObject("SELECT user_answer FROM exam_answer WHERE question_id = ?", String.class,
                single.getId())).isEqualTo("[\"C\"]");
        JsonNode saved = data(call(get("/api/exam-records/" + rid, u))).get("answers");
        assertThat(find(saved, single.getId(), "questionId").get("userAnswer").toString())
                .as("回显用新标签").isEqualTo("[\"" + singleAnswer + "\"]");

        // 考到一半管理员关掉打乱（有人考过也允许改），这场考试不受影响
        paper.put("shuffleOptions", false);
        JsonNode changed = data(call(put("/api/admin/papers/" + pid, admin, paper)));
        assertThat(changed.get("shuffleOptions").asBoolean()).isFalse();
        assertThat(changed.get("attempts").asLong()).isEqualTo(1);
        assertThat(find(data(call(get("/api/exam-records/" + rid, u))).get("questions"), single.getId(), "questionId")
                .get("optionMapping")).isEqualTo(sSingle.get("optionMapping"));

        JsonNode result = data(call(post("/api/exam-records/" + rid + "/submit", u, null)));
        assertThat(result.get("score").asInt()).isEqualTo(20);
        JsonNode item = find(result.get("items"), single.getId(), "questionId");
        assertThat(item.get("correct").asBoolean()).isTrue();
        assertThat(item.get("userAnswer").toString()).isEqualTo("[\"" + singleAnswer + "\"]");
        assertThat(item.get("correctAnswer").toString()).isEqualTo("[\"" + singleAnswer + "\"]");
        assertThat(item.get("optionMapping")).isEqualTo(sSingle.get("optionMapping"));
        assertOptionsFollowMapping(item);
        assertThat(jdbc.queryForObject("SELECT user_answer FROM answer_record WHERE question_id = ?", String.class,
                multi.getId())).isEqualTo("[\"B\",\"D\"]");

        // 下一场按新的设置：不打乱
        JsonNode next = data(call(post("/api/exams/" + pid + "/start", u, null)));
        assertThat(next.get("shuffleOptions").asBoolean()).isFalse();
        assertThat(find(next.get("questions"), single.getId(), "questionId").get("optionMapping").isNull()).isTrue();
        assertThat(data(call(get("/api/exams?categoryId=" + t[0].getId(), u))).get(0).get("shuffleOptions")
                .asBoolean()).isFalse();
    }
}
