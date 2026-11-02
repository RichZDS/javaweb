package com.richzds.quiz.support;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.richzds.quiz.common.JsonUtil;
import com.richzds.quiz.domain.FillOptions;
import com.richzds.quiz.domain.OptionItem;
import com.richzds.quiz.domain.QuestionRules;
import com.richzds.quiz.entity.Category;
import com.richzds.quiz.entity.Question;
import com.richzds.quiz.mapper.CategoryMapper;
import com.richzds.quiz.mapper.QuestionMapper;
import com.richzds.quiz.service.AdminBootstrap;

/**
 * 接口测试基类：完整 Spring 上下文 + MockMvc，test 配置（H2 内存库；CI 上换成 MySQL）。
 * 每个测试前清空全部业务表、时钟拨回 {@link MutableClock#DEFAULT}，测试之间互不影响。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
public abstract class ApiTestBase {

    private static final List<String> TABLES = List.of("app_user", "category", "question", "practice_session",
            "answer_record", "wrong_question", "favorite", "exam_paper", "paper_question", "paper_rule",
            "exam_record", "exam_answer", "question_feedback");

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected CategoryMapper categoryMapper;
    @Autowired
    protected QuestionMapper questionMapper;
    @Autowired
    protected AdminBootstrap adminBootstrap;

    @BeforeEach
    void resetState() {
        clock.reset();
        for (String table : TABLES) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    // ------------------------------------------------------------------ 账号

    /** 注册一个普通用户并返回登录信息。 */
    protected Login register(String username) throws Exception {
        JsonNode data = data(call(post("/api/auth/register", null,
                Map.of("username", username, "password", "secret123"))));
        return new Login(data.get("token").asText(), data.get("user").get("id").asLong());
    }

    /** 按配置建管理员（test 配置里是 admin / admin-test-pass）并登录。 */
    protected Login admin() throws Exception {
        adminBootstrap.ensureAdmin();
        JsonNode data = data(call(post("/api/auth/login", null,
                Map.of("username", "admin", "password", "admin-test-pass"))));
        return new Login(data.get("token").asText(), data.get("user").get("id").asLong());
    }

    protected void setTarget(Login user, Category exam) throws Exception {
        call(put("/api/me", user, Map.of("targetCategoryId", exam.getId())));
    }

    public record Login(String token, long id) {
    }

    // ------------------------------------------------------------------ 请求

    protected MockHttpServletRequestBuilder get(String path, Login user) {
        return auth(MockMvcRequestBuilders.get(path), user);
    }

    protected MockHttpServletRequestBuilder delete(String path, Login user) {
        return auth(MockMvcRequestBuilders.delete(path), user);
    }

    protected MockHttpServletRequestBuilder post(String path, Login user, Object body) throws Exception {
        return json(auth(MockMvcRequestBuilders.post(path), user), body);
    }

    protected MockHttpServletRequestBuilder put(String path, Login user, Object body) throws Exception {
        return json(auth(MockMvcRequestBuilders.put(path), user), body);
    }

    /** multipart 上传一个名为 file 的文件。 */
    protected MockHttpServletRequestBuilder upload(String path, Login user, String filename, String contentType,
                                                   byte[] content) {
        return auth(MockMvcRequestBuilders.multipart(path)
                .file(new MockMultipartFile("file", filename, contentType, content)), user);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, Object body) throws Exception {
        if (body != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return b;
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, Login user) {
        if (user != null) {
            b.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token());
        }
        return b;
    }

    /** 发请求，返回 {status, body}。 */
    protected Resp call(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        String text = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode body = text.isEmpty() ? objectMapper.nullNode() : objectMapper.readTree(text);
        return new Resp(result.getResponse().getStatus(), body);
    }

    /** 要求成功（2xx 且 code=OK）并返回 data。 */
    protected JsonNode data(Resp resp) {
        if (resp.status() / 100 != 2 || !"OK".equals(resp.body().path("code").asText())) {
            throw new AssertionError("请求失败: " + resp);
        }
        return resp.body().get("data");
    }

    public record Resp(int status, JsonNode body) {
        public String code() {
            return body.path("code").asText();
        }

        public JsonNode data() {
            return body.get("data");
        }
    }

    protected static List<Long> ids(JsonNode array, String field) {
        List<Long> out = new ArrayList<>();
        array.forEach(n -> out.add(n.get(field).asLong()));
        return out;
    }

    // ------------------------------------------------------------------ 造数据

    protected Category category(Category parent, String name, int sort) {
        Category c = new Category()
                .setParentId(parent == null ? null : parent.getId())
                .setName(name)
                .setLevel(parent == null ? 1 : parent.getLevel() + 1)
                .setSort(sort)
                .setCreatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        categoryMapper.insert(c);
        return c;
    }

    /** 建 考试 → 科目 → 章节 三级，返回 [考试, 科目, 章节]。 */
    protected Category[] tree(String examName) {
        Category exam = category(null, examName, 0);
        Category subject = category(exam, examName + "-科目", 0);
        Category chapter = category(subject, examName + "-章节", 0);
        return new Category[] {exam, subject, chapter};
    }

    protected Question single(Category chapter, String answer) {
        return choice(chapter, "SINGLE", 4, List.of(answer), "PUBLISHED");
    }

    protected Question multiple(Category chapter, String... answers) {
        return choice(chapter, "MULTIPLE", 5, List.of(answers), "PUBLISHED");
    }

    protected Question judge(Category chapter, boolean truth) {
        return save(base(chapter, "JUDGE", "判断题干").setAnswerJson(JsonUtil.write(List.of(truth ? "T" : "F"))));
    }

    protected Question fill(Category chapter, FillOptions opts, List<List<String>> accepted) {
        return save(base(chapter, "FILL", "填空题干 ____").setAnswerJson(JsonUtil.write(accepted))
                .setFillOptions(JsonUtil.write(opts)));
    }

    protected Question choice(Category chapter, String type, int optionCount, List<String> answer, String status) {
        List<OptionItem> options = new ArrayList<>();
        for (int i = 0; i < optionCount; i++) {
            options.add(new OptionItem(QuestionRules.keyAt(i), "选项 " + QuestionRules.keyAt(i)));
        }
        return save(base(chapter, type, type + " 题干").setOptionsJson(JsonUtil.write(options))
                .setAnswerJson(JsonUtil.write(answer)).setStatus(status));
    }

    protected Question base(Category chapter, String type, String stem) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return new Question().setCategoryId(chapter.getId()).setType(type).setStem(stem)
                .setAnalysis("解析：**原因**").setDifficulty(3).setStatus("PUBLISHED").setVersion(1).setSort(0)
                .setSourceNote("原创").setCreatedAt(now).setUpdatedAt(now);
    }

    protected Question save(Question q) {
        questionMapper.insert(q);
        return q;
    }

    protected List<Question> singles(Category chapter, int n) {
        List<Question> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(single(chapter, "A"));
        }
        return out;
    }
}
