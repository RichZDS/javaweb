package com.richzds.quiz.mock;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import com.richzds.quiz.domain.FillOptions;
import com.richzds.quiz.domain.OptionItem;
import com.richzds.quiz.domain.QuestionRules;
import com.richzds.quiz.domain.QuestionType;
import com.richzds.quiz.entity.Category;
import com.richzds.quiz.service.CategoryTree;

/** docs/mock-data.md 第 1–5 节的四个 JSON 文件，以及读取和校验。 */
public final class MockData {

    // ------------------------------------------------------------------ users.json

    public record UsersFile(Long randomSeed, LocalDate startDate, List<MockUser> users) {
    }

    public record MockUser(String username, String nickname, String target, LocalDate registeredOn,
                           LocalDate lastActiveOn, String persona, Double activeDaysPerWeek,
                           List<Integer> questionsPerDay, List<Integer> hours, Accuracy accuracy, Habits habits,
                           SwitchTarget switchTarget, Integer wrongRemoveThreshold, Boolean avatar,
                           Boolean disabled, List<Double> weekdayWeights) {

        /** 周一到周日的权重；没写时全是 1。 */
        public double[] weights() {
            double[] w = new double[7];
            for (int i = 0; i < 7; i++) {
                w[i] = weekdayWeights == null ? 1 : weekdayWeights.get(i);
            }
            return w;
        }
    }

    public record Accuracy(Double start, Double end, Map<String, Double> subjects) {
    }

    public record Habits(Double daily, Double random, Double sequential, Double wrongReview, Double recite,
                         Double shuffleOptions, Double favorite, Double feedback, Double examsPerMonth) {
    }

    public record SwitchTarget(LocalDate on, String to) {
    }

    // ------------------------------------------------------------------ questions.json / papers.json / texts.json

    public record QuestionsFile(List<MockQuestion> questions) {
    }

    public record MockQuestion(String chapter, LocalDate createdOn, String status, String type, String stem,
                               List<String> options, JsonNode answer, FillOptions fillOptions, String analysis,
                               Integer difficulty, List<String> tags, String source, String imageUrl) {
    }

    public record PapersFile(List<MockPaper> papers) {
    }

    public record MockPaper(String title, String exam, LocalDate createdOn, Boolean online, Integer durationMin,
                            Integer passScore, Boolean shuffleOptions, String description, List<MockRule> rules) {
    }

    public record MockRule(String category, String type, Integer count, Integer score) {
    }

    public record Texts(Map<String, List<String>> favoriteNotes, Map<String, List<String>> feedback,
                        Map<String, List<String>> adminReplies) {
    }

    // ------------------------------------------------------------------ 整体

    private final UsersFile users;
    private final List<MockQuestion> questions;
    private final List<MockPaper> papers;
    private final Texts texts;

    private MockData(UsersFile users, List<MockQuestion> questions, List<MockPaper> papers, Texts texts) {
        this.users = users;
        this.questions = questions;
        this.papers = papers;
        this.texts = texts;
    }

    public UsersFile users() {
        return users;
    }

    public List<MockQuestion> questions() {
        return questions;
    }

    public List<MockPaper> papers() {
        return papers;
    }

    public Texts texts() {
        return texts;
    }

    public long seed() {
        return users.randomSeed();
    }

    /** 读取 location 目录下的四个文件；未知字段直接报错，防止写错字段名被悄悄忽略。 */
    public static MockData load(ResourceLoader loader, ObjectMapper appMapper, String location) {
        ObjectMapper json = appMapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        String dir = location.endsWith("/") ? location : location + "/";
        UsersFile users = read(loader, json, dir + "users.json", UsersFile.class);
        // 题目和试卷可以暂时没有（产品经理还在写时先用学员数据预演）
        QuestionsFile questions = optional(loader, dir + "questions.json")
                ? read(loader, json, dir + "questions.json", QuestionsFile.class) : new QuestionsFile(List.of());
        PapersFile papers = optional(loader, dir + "papers.json")
                ? read(loader, json, dir + "papers.json", PapersFile.class) : new PapersFile(List.of());
        Texts texts = read(loader, json, dir + "texts.json", Texts.class);
        return new MockData(users,
                questions.questions() == null ? List.of() : questions.questions(),
                papers.papers() == null ? List.of() : papers.papers(), texts);
    }

    private static boolean optional(ResourceLoader loader, String path) {
        return loader.getResource(path).exists();
    }

    private static <T> T read(ResourceLoader loader, ObjectMapper json, String path, Class<T> type) {
        Resource r = loader.getResource(path);
        if (!r.exists()) {
            throw new IllegalStateException("找不到演示数据文件 " + path);
        }
        try (InputStream in = r.getInputStream()) {
            return json.readValue(in, type);
        } catch (IOException e) {
            throw new IllegalStateException("读取 " + path + " 失败：" + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ 校验

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,20}$");
    private static final List<String> RESERVED = List.of("qa_", "pm_", "fe_", "be_", "perf_", "deploy", "admin");
    private static final Set<String> FEEDBACK_TYPES = Set.of("ANSWER_WRONG", "STEM_WRONG", "ANALYSIS_UNCLEAR", "OTHER");

    /** 名称路径（“考试 / 科目 / 章节”）→ 分类，按示例题库里的名称。 */
    public static Map<String, Category> byPath(CategoryTree tree, List<Category> all) {
        Map<String, Category> out = new HashMap<>();
        for (Category c : all) {
            out.put(tree.path(c.getId()), c);
        }
        return out;
    }

    /** 全部问题一次列出；没有问题返回空列表。until 是模拟的结束日（北京时间）。 */
    public List<String> validate(Map<String, Category> paths, LocalDate until) {
        List<String> errors = new ArrayList<>();
        if (users.randomSeed() == null) {
            errors.add("users.json：缺 randomSeed");
        }
        LocalDate start = users.startDate();
        if (start == null) {
            errors.add("users.json：缺 startDate");
            return errors;
        }
        if (users.users() == null || users.users().isEmpty()) {
            errors.add("users.json：users 为空");
            return errors;
        }
        Set<String> names = new HashSet<>();
        Set<String> subjectNames = new HashSet<>();
        for (Category c : paths.values()) {
            if (c.getLevel() == CategoryTree.LEVEL_SUBJECT) {
                subjectNames.add(c.getName());
            }
        }
        int i = 0;
        for (MockUser u : users.users()) {
            i++;
            String at = "users[" + i + "]" + (u.username() == null ? "" : "（" + u.username() + "）") + "：";
            if (u.username() == null || !USERNAME.matcher(u.username()).matches()) {
                errors.add(at + "username 必须是 3–20 位字母、数字、下划线");
            } else {
                String lower = u.username().toLowerCase();
                if (RESERVED.stream().anyMatch(lower::startsWith)) {
                    errors.add(at + "username 不能以 " + RESERVED + " 开头");
                }
                if (!names.add(lower)) {
                    errors.add(at + "username 重复");
                }
            }
            if (u.nickname() == null || u.nickname().isBlank() || u.nickname().length() > 30) {
                errors.add(at + "nickname 为 1–30 字");
            }
            checkExam(paths, u.target(), at + "target", errors);
            if (u.registeredOn() == null || u.registeredOn().isBefore(start) || u.registeredOn().isAfter(until)) {
                errors.add(at + "registeredOn 必须在 " + start + " 到 " + until + " 之间");
            }
            if (u.lastActiveOn() != null && u.registeredOn() != null && u.lastActiveOn().isBefore(u.registeredOn())) {
                errors.add(at + "lastActiveOn 早于 registeredOn");
            }
            if (u.activeDaysPerWeek() == null || u.activeDaysPerWeek() < 0 || u.activeDaysPerWeek() > 7) {
                errors.add(at + "activeDaysPerWeek 为 0–7");
            }
            List<Integer> q = u.questionsPerDay();
            if (q == null || q.size() != 2 || q.get(0) == null || q.get(1) == null || q.get(0) < 0 || q.get(0) > q.get(1)) {
                errors.add(at + "questionsPerDay 写成 [最少, 最多]");
            }
            if (u.hours() == null || u.hours().isEmpty() || u.hours().stream().anyMatch(h -> h == null || h < 0 || h > 23)) {
                errors.add(at + "hours 是 0–23 的小时列表，不能为空");
            }
            Accuracy a = u.accuracy();
            if (a == null || !rate(a.start()) || !rate(a.end())) {
                errors.add(at + "accuracy.start / end 为 0–1");
            } else if (a.subjects() != null) {
                for (String s : a.subjects().keySet()) {
                    if (!subjectNames.contains(s)) {
                        errors.add(at + "accuracy.subjects 里的科目不存在：" + s);
                    }
                }
            }
            Habits h = u.habits();
            if (h == null || !rate(h.daily()) || !weight(h.random()) || !weight(h.sequential())
                    || !weight(h.wrongReview()) || !rate(h.recite()) || !rate(h.shuffleOptions())
                    || !rate(h.favorite()) || !rate(h.feedback()) || !weight(h.examsPerMonth())) {
                errors.add(at + "habits 缺字段或超出范围（概率 0–1，比重和每月考试次数不能为负）");
            }
            if (u.switchTarget() != null) {
                checkExam(paths, u.switchTarget().to(), at + "switchTarget.to", errors);
                LocalDate on = u.switchTarget().on();
                if (on == null || u.registeredOn() == null || !on.isAfter(u.registeredOn()) || on.isAfter(until)) {
                    errors.add(at + "switchTarget.on 必须晚于 registeredOn、不晚于 " + until);
                }
            }
            Integer th = u.wrongRemoveThreshold();
            if (th != null && (th < 1 || th > 10)) {
                errors.add(at + "wrongRemoveThreshold 为 1–10");
            }
            List<Double> w = u.weekdayWeights();
            if (w != null && (w.size() != 7 || w.stream().anyMatch(x -> x == null || x < 0)
                    || w.stream().mapToDouble(Double::doubleValue).sum() <= 0)) {
                errors.add(at + "weekdayWeights 是周一到周日 7 个非负数，不能全为 0");
            }
        }
        i = 0;
        for (MockQuestion q : questions) {
            i++;
            String at = "questions[" + i + "]：";
            Category c = q.chapter() == null ? null : paths.get(q.chapter());
            if (c == null || c.getLevel() != CategoryTree.LEVEL_CHAPTER) {
                errors.add(at + "chapter 不是示例题库里的三级分类：" + q.chapter());
            }
            if (q.createdOn() == null || q.createdOn().isBefore(start) || q.createdOn().isAfter(until)) {
                errors.add(at + "createdOn 必须在 " + start + " 到 " + until + " 之间");
            }
            if (!Set.of("DRAFT", "PUBLISHED", "ARCHIVED").contains(String.valueOf(q.status()))) {
                errors.add(at + "status 只能是 DRAFT、PUBLISHED、ARCHIVED");
            }
            String bad = checkQuestion(q);
            if (bad != null) {
                errors.add(at + bad);
            }
        }
        i = 0;
        for (MockPaper p : papers) {
            i++;
            String at = "papers[" + i + "]" + (p.title() == null ? "" : "（" + p.title() + "）") + "：";
            Category exam = checkExam(paths, p.exam(), at + "exam", errors);
            if (p.title() == null || p.title().isBlank()) {
                errors.add(at + "title 不能为空");
            }
            if (p.createdOn() == null || p.createdOn().isBefore(start) || p.createdOn().isAfter(until)) {
                errors.add(at + "createdOn 必须在 " + start + " 到 " + until + " 之间");
            }
            if (p.durationMin() == null || p.durationMin() < 1 || p.passScore() == null || p.passScore() < 0) {
                errors.add(at + "durationMin、passScore 必填");
            }
            if (p.rules() == null || p.rules().isEmpty()) {
                errors.add(at + "rules 不能为空（只用规则卷）");
                continue;
            }
            for (MockRule r : p.rules()) {
                Category c = r.category() == null ? null : paths.get(r.category());
                if (c == null) {
                    errors.add(at + "规则分类不存在：" + r.category());
                } else if (exam != null && !r.category().equals(exam.getName())
                        && !r.category().startsWith(exam.getName() + " / ")) {
                    errors.add(at + "规则分类不属于这份试卷的考试：" + r.category());
                }
                if (r.count() == null || r.count() < 1 || r.score() == null || r.score() < 1) {
                    errors.add(at + "规则的 count、score 至少 1");
                }
                try {
                    QuestionType.valueOf(String.valueOf(r.type()));
                } catch (IllegalArgumentException e) {
                    errors.add(at + "规则题型不正确：" + r.type());
                }
            }
        }
        if (texts == null) {
            errors.add("texts.json：为空");
        } else {
            if (texts.favoriteNotes() == null || texts.favoriteNotes().getOrDefault("*", List.of()).isEmpty()) {
                errors.add("texts.json：favoriteNotes 必须有通用组 \"*\"");
            }
            for (String t : FEEDBACK_TYPES) {
                if (texts.feedback() == null || texts.feedback().getOrDefault(t, List.of()).isEmpty()) {
                    errors.add("texts.json：feedback 缺 " + t);
                }
            }
            for (String t : List.of("RESOLVED", "REJECTED")) {
                if (texts.adminReplies() == null || texts.adminReplies().getOrDefault(t, List.of()).isEmpty()) {
                    errors.add("texts.json：adminReplies 缺 " + t);
                }
            }
        }
        return errors;
    }

    private static Category checkExam(Map<String, Category> paths, String name, String at, List<String> errors) {
        Category c = name == null ? null : paths.get(name);
        if (c == null || c.getLevel() != CategoryTree.LEVEL_EXAM) {
            errors.add(at + " 不是示例题库里的一级分类：" + name);
            return null;
        }
        return c;
    }

    /** 和后台加题同一套规则（QuestionRules）；返回错误说明，没问题返回 null。 */
    static String checkQuestion(MockQuestion q) {
        QuestionType type;
        try {
            type = QuestionType.valueOf(String.valueOf(q.type()));
        } catch (IllegalArgumentException e) {
            return "题型不正确：" + q.type();
        }
        if (q.stem() == null || q.stem().isBlank() || q.analysis() == null || q.analysis().isBlank()) {
            return "题干和解析都不能为空";
        }
        if (q.difficulty() != null && (q.difficulty() < 1 || q.difficulty() > 5)) {
            return "难度为 1–5";
        }
        List<OptionItem> options = options(q);
        try {
            return QuestionRules.check(type, options, correct(q), accepted(q));
        } catch (IllegalArgumentException e) {
            return "answer 格式不正确";
        }
    }

    static List<OptionItem> options(MockQuestion q) {
        List<OptionItem> out = new ArrayList<>();
        if (q.options() != null) {
            for (int i = 0; i < q.options().size(); i++) {
                out.add(new OptionItem(QuestionRules.keyAt(i), q.options().get(i)));
            }
        }
        return out;
    }

    static List<String> correct(MockQuestion q) {
        if ("FILL".equals(q.type()) || q.answer() == null || !q.answer().isArray()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        q.answer().forEach(n -> {
            if (!n.isTextual()) {
                throw new IllegalArgumentException();
            }
            out.add(n.asText());
        });
        return out;
    }

    static List<List<String>> accepted(MockQuestion q) {
        if (!"FILL".equals(q.type()) || q.answer() == null || !q.answer().isArray()) {
            return null;
        }
        List<List<String>> out = new ArrayList<>();
        q.answer().forEach(blank -> {
            if (!blank.isArray()) {
                throw new IllegalArgumentException();
            }
            List<String> alts = new ArrayList<>();
            blank.forEach(n -> alts.add(n.asText()));
            out.add(alts);
        });
        return out;
    }

    private static boolean rate(Double x) {
        return x != null && x >= 0 && x <= 1;
    }

    private static boolean weight(Double x) {
        return x != null && x >= 0;
    }
}
