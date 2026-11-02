package com.richzds.quiz.perf;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.richzds.quiz.security.JwtService;

/**
 * 接口耗时实测（给论文用）。不在 mvn verify 里跑（类名不以 Test 结尾），单独执行：
 * <pre>heavy sozai-lock heavy -- mvn -B -ntp -Dtest=PerformanceBenchmark test</pre>
 * 起一个真的 HTTP 服务（端口 18030），用 {@link PerfDataGenerator} 造数据，每个接口先预热 10 次，
 * 再测 50 次，取中位数和 95 分位（最近秩法），结果写到 target/perf/performance-result.md。
 * 用 test 配置：H2 内存库（MySQL 兼容模式），不代表服务器上 MySQL 的数字。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = "server.port=18030")
@ActiveProfiles("test")
class PerformanceBenchmark {

    static final int WARMUP = 10;
    static final int RUNS = 50;
    static final double TARGET_MS = 500;
    private static final List<String> TABLES = List.of("app_user", "category", "question", "practice_session",
            "answer_record", "wrong_question", "favorite", "exam_paper", "paper_question", "paper_rule",
            "exam_record", "exam_answer", "question_feedback");

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JwtService jwtService;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    Clock clock;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /** 一次测量的结果（毫秒）。 */
    record Result(String name, String request, double median, double p95, double max) {
    }

    @Test
    void measure() throws Exception {
        for (String t : TABLES) {
            jdbc.update("DELETE FROM " + t);
        }
        long genStart = System.nanoTime();
        PerfDataGenerator.Data data = new PerfDataGenerator(jdbc).generate(PerfDataGenerator.nowUtc(clock));
        double genSec = (System.nanoTime() - genStart) / 1e9;
        long exam = data.examIds().get(0);
        long subject = data.subjectIds().get(0);
        long rulePaper = data.rulePaperByExam().get(exam);
        // 目标考试是第一个考试的学员（下标 0、3、6……）
        List<Long> users = new ArrayList<>();
        for (int i = 0; i < data.userIds().size(); i += PerfDataGenerator.EXAMS) {
            users.add(data.userIds().get(i));
        }
        assertThat(users).hasSizeGreaterThanOrEqualTo(WARMUP + RUNS);
        Map<Long, String> tokens = new LinkedHashMap<>();
        for (Long u : data.userIds()) {
            tokens.put(u, jwtService.issue(u).token());
        }
        String t0 = tokens.get(users.get(0));
        List<Result> results = new ArrayList<>();

        results.add(time("分类树（带本人进度）", "GET /api/categories?rootId=",
                i -> get("/api/categories?rootId=" + exam, t0)));
        results.add(time("开随机练习（20 题）", "POST /api/practice/sessions",
                i -> post("/api/practice/sessions", tokens.get(users.get(i)),
                        "{\"mode\":\"RANDOM\",\"categoryIds\":[" + subject + "],\"count\":20}")));

        // 提交答案：一次 50 题的练习，预热和测量各用一次练习里的题
        List<long[]> submits = new ArrayList<>(); // [sessionId, questionId]
        for (int s = 0; s < 2; s++) {
            JsonNode session = json(post("/api/practice/sessions", t0,
                    "{\"mode\":\"RANDOM\",\"categoryIds\":[" + exam + "],\"count\":50}")).get("data");
            for (JsonNode q : session.get("questions")) {
                submits.add(new long[] {session.get("id").asLong(), q.get("id").asLong()});
            }
        }
        results.add(time("提交答案（含判分、错题本）", "POST /api/practice/sessions/{id}/answers", i -> {
            long[] s = submits.get(i);
            return post("/api/practice/sessions/" + s[0] + "/answers", t0,
                    "{\"questionId\":" + s[1] + ",\"userAnswer\":[\"A\"],\"timeSpentSec\":10}");
        }));
        results.add(time("统计总览", "GET /api/stats/overview", i -> get("/api/stats/overview", t0)));
        results.add(time("统计按日（30 天）", "GET /api/stats/daily?days=30", i -> get("/api/stats/daily?days=30", t0)));
        results.add(time("薄弱点（30 天）", "GET /api/stats/weak?days=30", i -> get("/api/stats/weak?days=30", t0)));

        // 开考：规则卷（单选 30 + 多选 10 + 判断 10），每次换一个学员，都是新建
        List<Long> records = new ArrayList<>();
        results.add(time("开考（规则卷 50 题抽题 + 快照）", "POST /api/exams/{paperId}/start", i -> {
            String body = post("/api/exams/" + rulePaper + "/start", tokens.get(users.get(i)), null);
            records.add(json(body).get("data").get("recordId").asLong());
            return body;
        }));
        // 交卷前给每场的每道题存一份答案（直接写库，不计时）
        Timestamp now = Timestamp.valueOf(LocalDateTime.now(clock));
        for (Long rid : records) {
            JsonNode snapshot = objectMapper.readTree(jdbc.queryForObject(
                    "SELECT question_snapshot FROM exam_record WHERE id = ?", String.class, rid));
            List<Object[]> rows = new ArrayList<>();
            for (JsonNode q : snapshot.get("questions")) {
                rows.add(new Object[] {rid, q.get("questionId").asLong(), "[\"A\"]", 1, false, now});
            }
            jdbc.batchUpdate("INSERT INTO exam_answer (record_id, question_id, user_answer, version, marked, received_at) "
                    + "VALUES (?,?,?,?,?,?)", rows);
        }
        results.add(time("交卷结算（50 题判分、作答记录、错题本）", "POST /api/exam-records/{id}/submit", i -> {
            long rid = records.get(i);
            long owner = jdbc.queryForObject("SELECT user_id FROM exam_record WHERE id = ?", Long.class, rid);
            return post("/api/exam-records/" + rid + "/submit", tokens.get(owner), null);
        }));

        write(results, data, genSec);
    }

    // ------------------------------------------------------------------ 计时

    private Result time(String name, String request, IntFunction<String> call) {
        for (int i = 0; i < WARMUP; i++) {
            call.apply(i);
        }
        double[] ms = new double[RUNS];
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            call.apply(WARMUP + i);
            ms[i] = (System.nanoTime() - start) / 1e6;
        }
        Arrays.sort(ms);
        double median = (ms[RUNS / 2 - 1] + ms[RUNS / 2]) / 2;
        double p95 = ms[(int) Math.ceil(0.95 * RUNS) - 1];
        return new Result(name, request, median, p95, ms[RUNS - 1]);
    }

    private String get(String path, String token) {
        return send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18030" + path)).GET(), token);
    }

    private String post(String path, String token, String body) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:18030" + path))
                .POST(body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (body != null) {
            b.header("Content-Type", "application/json");
        }
        return send(b, token);
    }

    /** 发请求；非 2xx 直接让测试失败，保证测到的都是成功的请求。 */
    private String send(HttpRequest.Builder b, String token) {
        try {
            HttpResponse<String> r = http.send(b.header("Authorization", "Bearer " + token)
                    .timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (r.statusCode() / 100 != 2) {
                throw new AssertionError("请求失败 " + r.statusCode() + "：" + r.body());
            }
            return r.body();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private JsonNode json(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void write(List<Result> results, PerfDataGenerator.Data data, double genSec) throws Exception {
        StringBuilder md = new StringBuilder();
        md.append("| 接口 | 请求 | 中位数 (ms) | 95 分位 (ms) | 最大 (ms) | ≤ 500 ms |\n");
        md.append("| --- | --- | ---: | ---: | ---: | --- |\n");
        for (Result r : results) {
            md.append(String.format("| %s | `%s` | %.1f | %.1f | %.1f | %s |%n", r.name(), r.request(), r.median(),
                    r.p95(), r.max(), r.p95() <= TARGET_MS ? "是" : "否"));
        }
        md.append("\n数据量：").append(data.rowCounts()).append(String.format("；造数据耗时 %.1f 秒%n", genSec));
        md.append("JVM：").append(System.getProperty("java.vm.name")).append(' ')
                .append(System.getProperty("java.version")).append("，最大堆 ")
                .append(Runtime.getRuntime().maxMemory() / 1024 / 1024).append(" MB，CPU 核数 ")
                .append(Runtime.getRuntime().availableProcessors()).append('\n');
        Path out = Path.of("target", "perf", "performance-result.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, md.toString(), StandardCharsets.UTF_8);
        System.out.println("\n===== 性能结果 =====\n" + md);
        assertThat(results).hasSize(8);
    }
}
