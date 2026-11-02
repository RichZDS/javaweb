package com.richzds.quiz.perf;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 性能测试用的数据生成器（只在测试里用）：3 个考试 × 4 科目 × 5 章节，1 万道题，200 个学员，
 * 10 万条作答记录（近 60 天，每 20 条一次已完成的练习，共 5000 次），以及对应的错题本，
 * 和每个考试一份固定卷、一份规则卷。
 * 随机数种子固定，每次生成的数据完全一样，结果可复现。直接用 JDBC 批量插入，不走接口。
 */
public final class PerfDataGenerator {

    public static final long SEED = 20261102L;
    public static final int EXAMS = 3;
    public static final int SUBJECTS_PER_EXAM = 4;
    public static final int CHAPTERS_PER_SUBJECT = 5;
    public static final int QUESTIONS = 10_000;
    public static final int USERS = 200;
    public static final int ANSWERS = 100_000;
    public static final int ANSWERS_PER_SESSION = 20;
    public static final int HISTORY_DAYS = 60;

    /** 生成结果里测试要用到的 id。 */
    public record Data(List<Long> examIds, List<Long> subjectIds, List<Long> chapterIds, List<Long> userIds,
                       Map<Long, Long> fixedPaperByExam, Map<Long, Long> rulePaperByExam,
                       Map<String, Integer> rowCounts) {
    }

    private final JdbcTemplate jdbc;
    private final Random random = new Random(SEED);

    public PerfDataGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Data generate(LocalDateTime nowUtc) {
        Timestamp now = Timestamp.valueOf(nowUtc);
        // ---------------- 分类
        List<Long> exams = new ArrayList<>();
        List<Long> subjects = new ArrayList<>();
        List<Long> chapters = new ArrayList<>();
        Map<Long, Long> rootOfChapter = new HashMap<>();
        for (int e = 0; e < EXAMS; e++) {
            long exam = insertCategory(null, "性能考试" + (e + 1), 1, e, now);
            exams.add(exam);
            for (int s = 0; s < SUBJECTS_PER_EXAM; s++) {
                long subject = insertCategory(exam, "科目" + (e + 1) + "-" + (s + 1), 2, s, now);
                subjects.add(subject);
                for (int c = 0; c < CHAPTERS_PER_SUBJECT; c++) {
                    long chapter = insertCategory(subject, "章节" + (e + 1) + "-" + (s + 1) + "-" + (c + 1), 3, c, now);
                    chapters.add(chapter);
                    rootOfChapter.put(chapter, exam);
                }
            }
        }

        // ---------------- 题目：单选 55%、多选 20%、判断 15%、填空 10%；每 10 道带一段 Java 代码
        List<Object[]> qRows = new ArrayList<>(QUESTIONS);
        for (int i = 0; i < QUESTIONS; i++) {
            long chapter = chapters.get(i % chapters.size());
            int roll = random.nextInt(100);
            String type = roll < 55 ? "SINGLE" : roll < 75 ? "MULTIPLE" : roll < 90 ? "JUDGE" : "FILL";
            String stem = "性能测试题 #" + i + "：下面关于**数据结构**的说法哪一项正确？"
                    + (i % 10 == 0 ? "\n\n```java\nint[] a = new int[" + i + "];\nfor (int k = 0; k < a.length; k++) {\n"
                    + "    a[k] = k * 2; // 注释\n}\nSystem.out.println(a.length);\n```" : "");
            String options = switch (type) {
                case "SINGLE", "MULTIPLE" -> "[{\"key\":\"A\",\"text\":\"选项甲 `O(1)`\"},{\"key\":\"B\",\"text\":\"选项乙\"},"
                        + "{\"key\":\"C\",\"text\":\"选项丙 **重点**\"},{\"key\":\"D\",\"text\":\"选项丁\"}]";
                default -> null;
            };
            String answer = switch (type) {
                case "SINGLE" -> "[\"A\"]";
                case "MULTIPLE" -> "[\"A\",\"C\"]";
                case "JUDGE" -> "[\"T\"]";
                default -> "[[\"int\",\"Integer\"]]";
            };
            qRows.add(new Object[] {chapter, type, stem, options, answer,
                    "FILL".equals(type) ? "{\"ignoreCase\":true,\"ignoreSpaces\":true}" : null,
                    "解析：因为 **原因**，所以选它。", 1 + random.nextInt(5), "性能,标签" + (i % 7), "原创", "PUBLISHED", 1,
                    i / chapters.size(), now, now});
        }
        jdbc.batchUpdate("INSERT INTO question (category_id, type, stem, options_json, answer_json, fill_options, "
                + "analysis, difficulty, tags, source_note, status, version, sort, created_at, updated_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", qRows);
        Map<Long, List<long[]>> questionsByExam = new HashMap<>(); // exam → [id, chapter]
        Map<Long, String> typeOf = new HashMap<>();
        jdbc.query("SELECT id, category_id, type FROM question", rs -> {
            long id = rs.getLong(1);
            long chapter = rs.getLong(2);
            questionsByExam.computeIfAbsent(rootOfChapter.get(chapter), k -> new ArrayList<>())
                    .add(new long[] {id, chapter});
            typeOf.put(id, rs.getString(3));
        });

        // ---------------- 学员：同一个 BCrypt 哈希（只算一次），目标考试轮流分配
        String hash = new BCryptPasswordEncoder().encode("perf-password");
        List<Object[]> uRows = new ArrayList<>();
        for (int u = 0; u < USERS; u++) {
            uRows.add(new Object[] {String.format("perf_u%03d", u), hash, "学员" + u, "USER", "ACTIVE",
                    exams.get(u % EXAMS), 3, now, now, now});
        }
        jdbc.batchUpdate("INSERT INTO app_user (username, password_hash, nickname, role, status, target_category_id, "
                + "wrong_remove_threshold, last_active_at, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)", uRows);
        List<Long> users = jdbc.queryForList("SELECT id FROM app_user WHERE username LIKE 'perf\\_u%' ORDER BY id",
                Long.class);

        // ---------------- 作答记录：每人 500 条，每 20 条是一次已完成的练习（同一次里题不重复），时间分布在近 60 天。
        // 先建练习记录再用它们的 id 作 source_id，避免和测试时新建的练习撞号。
        int perUser = ANSWERS / USERS;
        record Block(long userId, long exam, LocalDateTime base, List<long[]> questions, List<Boolean> correct) {
        }
        List<Block> blocks = new ArrayList<>();
        for (int u = 0; u < users.size(); u++) {
            long userId = users.get(u);
            long exam = exams.get(u % EXAMS);
            List<long[]> pool = questionsByExam.get(exam);
            for (int done = 0; done < perUser; done += ANSWERS_PER_SESSION) {
                LocalDateTime base = nowUtc.minusSeconds(random.nextInt(HISTORY_DAYS * 24 * 3600));
                Set<Long> used = new HashSet<>();
                List<long[]> qs = new ArrayList<>();
                List<Boolean> correct = new ArrayList<>();
                while (qs.size() < ANSWERS_PER_SESSION) {
                    long[] q = pool.get(random.nextInt(pool.size()));
                    if (used.add(q[0])) {
                        qs.add(q);
                        correct.add(random.nextInt(100) < 70);
                    }
                }
                blocks.add(new Block(userId, exam, base, qs, correct));
            }
        }
        List<Object[]> sRows = new ArrayList<>(blocks.size());
        for (Block b : blocks) {
            String ids = b.questions().stream().map(q -> Long.toString(q[0]))
                    .collect(java.util.stream.Collectors.joining(",", "[", "]"));
            Timestamp started = Timestamp.valueOf(b.base());
            Timestamp completed = Timestamp.valueOf(b.base().plusSeconds(ANSWERS_PER_SESSION * 30L));
            sRows.add(new Object[] {b.userId(), "RANDOM", false, ids, ANSWERS_PER_SESSION - 1, "COMPLETED", b.exam(),
                    started, completed, completed});
        }
        jdbc.batchUpdate("INSERT INTO practice_session (user_id, mode, recite_mode, question_ids, current_index, status, "
                + "root_category_id, started_at, completed_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)", sRows);
        List<Long> sessionIds = jdbc.queryForList("SELECT id FROM practice_session ORDER BY id", Long.class);
        List<Object[]> aRows = new ArrayList<>(ANSWERS);
        Map<String, Object[]> wrong = new LinkedHashMap<>(); // userId:questionId → 错题行
        for (int bi = 0; bi < blocks.size(); bi++) {
            Block b = blocks.get(bi);
            for (int k = 0; k < ANSWERS_PER_SESSION; k++) {
                long[] q = b.questions().get(k);
                boolean correct = b.correct().get(k);
                Timestamp at = Timestamp.valueOf(b.base().plusSeconds(k * 30L));
                aRows.add(new Object[] {b.userId(), q[0], "PRACTICE", sessionIds.get(bi),
                        correct ? "[\"A\"]" : "[\"B\"]", true, correct, q[1], b.exam(), 30, at});
                if (!correct) {
                    String key = b.userId() + ":" + q[0];
                    Object[] row = wrong.get(key);
                    if (row == null) {
                        wrong.put(key, new Object[] {b.userId(), q[0], 1, 0, "[\"B\"]", at, false, at});
                    } else {
                        row[2] = (Integer) row[2] + 1;
                    }
                }
            }
        }
        for (int from = 0; from < aRows.size(); from += 5000) {
            jdbc.batchUpdate("INSERT INTO answer_record (user_id, question_id, source, source_id, user_answer, answered, "
                            + "is_correct, category_id_snapshot, root_category_id, time_spent_sec, graded_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    aRows.subList(from, Math.min(from + 5000, aRows.size())));
        }
        jdbc.batchUpdate("INSERT INTO wrong_question (user_id, question_id, wrong_count, correct_streak, "
                + "last_wrong_answer, last_wrong_at, removed, created_at) VALUES (?,?,?,?,?,?,?,?)",
                new ArrayList<>(wrong.values()));

        // ---------------- 试卷：每个考试一份固定卷（50 题 × 2 分）和一份规则卷（单选 30×2、多选 10×3、判断 10×1）
        Map<Long, Long> fixed = new HashMap<>();
        Map<Long, Long> rule = new HashMap<>();
        for (int e = 0; e < EXAMS; e++) {
            long exam = exams.get(e);
            long fixedId = insertPaper("性能固定卷" + (e + 1), exam, "FIXED", 100, now);
            List<long[]> pool = new ArrayList<>(questionsByExam.get(exam));
            java.util.Collections.shuffle(pool, random);
            List<Object[]> pq = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                pq.add(new Object[] {fixedId, pool.get(i)[0], 2, i});
            }
            jdbc.batchUpdate("INSERT INTO paper_question (paper_id, question_id, score, sort) VALUES (?,?,?,?)", pq);
            fixed.put(exam, fixedId);
            long ruleId = insertPaper("性能规则卷" + (e + 1), exam, "RULE", 100, now);
            jdbc.update("INSERT INTO paper_rule (paper_id, category_id, type, count, score, sort) VALUES (?,?,?,?,?,?)",
                    ruleId, exam, "SINGLE", 30, 2, 0);
            jdbc.update("INSERT INTO paper_rule (paper_id, category_id, type, count, score, sort) VALUES (?,?,?,?,?,?)",
                    ruleId, subjects.get(e * SUBJECTS_PER_EXAM), "MULTIPLE", 10, 3, 1);
            jdbc.update("INSERT INTO paper_rule (paper_id, category_id, type, count, score, sort) VALUES (?,?,?,?,?,?)",
                    ruleId, exam, "JUDGE", 10, 1, 2);
            rule.put(exam, ruleId);
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String t : List.of("category", "question", "app_user", "practice_session", "answer_record",
                "wrong_question", "exam_paper", "paper_question", "paper_rule")) {
            counts.put(t, jdbc.queryForObject("SELECT COUNT(*) FROM " + t, Integer.class));
        }
        if (typeOf.size() != QUESTIONS) {
            throw new IllegalStateException("题目数不对：" + typeOf.size());
        }
        return new Data(exams, subjects, chapters, users, fixed, rule, counts);
    }

    private long insertCategory(Long parent, String name, int level, int sort, Timestamp now) {
        jdbc.update("INSERT INTO category (parent_id, name, level, sort, created_at) VALUES (?,?,?,?,?)",
                parent, name, level, sort, now);
        return jdbc.queryForObject("SELECT MAX(id) FROM category", Long.class);
    }

    private long insertPaper(String title, long exam, String mode, int total, Timestamp now) {
        jdbc.update("INSERT INTO exam_paper (title, category_id, description, mode, duration_min, total_score, "
                + "pass_score, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                title, exam, "性能测试卷", mode, 60, total, 60, "ONLINE", now, now);
        return jdbc.queryForObject("SELECT MAX(id) FROM exam_paper", Long.class);
    }

    /** UTC 的当前时刻（截到毫秒）。 */
    public static LocalDateTime nowUtc(java.time.Clock clock) {
        return LocalDateTime.ofInstant(clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS),
                ZoneOffset.UTC);
    }
}
