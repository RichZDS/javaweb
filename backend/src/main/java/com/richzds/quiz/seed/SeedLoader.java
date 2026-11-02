package com.richzds.quiz.seed;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.richzds.quiz.common.JsonUtil;
import com.richzds.quiz.common.TimeUtil;
import com.richzds.quiz.config.AppProperties;
import com.richzds.quiz.domain.FillOptions;
import com.richzds.quiz.domain.OptionItem;
import com.richzds.quiz.domain.QuestionRules;
import com.richzds.quiz.domain.QuestionType;
import com.richzds.quiz.entity.Category;
import com.richzds.quiz.entity.ExamPaper;
import com.richzds.quiz.entity.PaperQuestion;
import com.richzds.quiz.entity.PaperRule;
import com.richzds.quiz.entity.Question;
import com.richzds.quiz.exam.RuleDrawer;
import com.richzds.quiz.mapper.CategoryMapper;
import com.richzds.quiz.mapper.ExamPaperMapper;
import com.richzds.quiz.mapper.PaperQuestionMapper;
import com.richzds.quiz.mapper.PaperRuleMapper;
import com.richzds.quiz.mapper.QuestionMapper;

/**
 * 示例题库导入（格式见 docs/seed-format.md）：app.seed.enabled=true 且分类表为空时，
 * 按文件名顺序读 app.seed.location（默认 classpath:seed/）下的全部 *.json，
 * 建分类、题目（PUBLISHED）和试卷（ONLINE）。
 * 全部在一个事务里；任何一处不合格就整体回滚并让启动失败，错误信息指明文件和条目。
 */
@Component
@Order(20)
public class SeedLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);
    private static final Pattern KEY = Pattern.compile("^[a-z0-9-]+$");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private static final TypeReference<List<List<String>>> NESTED_LIST = new TypeReference<>() { };

    private final CategoryMapper categoryMapper;
    private final QuestionMapper questionMapper;
    private final ExamPaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final PaperRuleMapper paperRuleMapper;
    private final TransactionTemplate tx;
    private final AppProperties props;
    private final Clock clock;
    private final ObjectMapper json = JsonUtil.mapper();

    public SeedLoader(CategoryMapper categoryMapper, QuestionMapper questionMapper, ExamPaperMapper paperMapper,
                      PaperQuestionMapper paperQuestionMapper, PaperRuleMapper paperRuleMapper,
                      TransactionTemplate tx, AppProperties props, Clock clock) {
        this.categoryMapper = categoryMapper;
        this.questionMapper = questionMapper;
        this.paperMapper = paperMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.paperRuleMapper = paperRuleMapper;
        this.tx = tx;
        this.props = props;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ 文件结构

    public record SeedFile(List<SeedCategory> categories, List<SeedQuestion> questions, List<SeedPaper> papers) {
    }

    public record SeedCategory(String key, String name, List<SeedCategory> children) {
    }

    public record SeedQuestion(String ref, String category, String type, String stem, List<String> options,
                               JsonNode answer, FillOptions fillOptions, String analysis, Integer difficulty,
                               List<String> tags, String source, String imageUrl) {
    }

    public record SeedPaper(String title, String category, String mode, Integer durationMin, Integer passScore,
                            String description, List<SeedRule> rules, List<SeedPaperQuestion> questions) {
    }

    public record SeedRule(String category, String type, Integer count, Integer score) {
    }

    public record SeedPaperQuestion(String ref, Integer score) {
    }

    /** 导入结果计数。 */
    public record Result(int files, int categories, int questions, int papers) {
    }

    // ------------------------------------------------------------------ 入口

    @Override
    public void run(ApplicationArguments args) {
        if (!props.seed().enabled()) {
            return;
        }
        if (categoryMapper.selectCount(null) > 0) {
            log.info("分类表不为空，跳过示例题库导入");
            return;
        }
        Result r = load();
        log.info("示例题库导入完成：{} 个文件，{} 个分类，{} 道题，{} 份试卷",
                r.files(), r.categories(), r.questions(), r.papers());
    }

    /** 读取并导入全部种子文件（调用方负责判断是否该导入）。 */
    public Result load() {
        List<Resource> files = resources();
        return tx.execute(status -> new Importer().importAll(files));
    }

    private List<Resource> resources() {
        String dir = props.seed().location();
        String pattern = (dir.endsWith("/") ? dir : dir + "/") + "*.json";
        try {
            Resource[] found = new PathMatchingResourcePatternResolver().getResources(pattern);
            return Arrays.stream(found)
                    .filter(Resource::isReadable)
                    .sorted(Comparator.comparing(r -> r.getFilename() == null ? "" : r.getFilename()))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("读取示例题库目录失败：" + pattern, e);
        }
    }

    /** 一次导入的状态：key → 分类、ref → 题目，以及每个章节里按题型的题数（校验规则卷用）。 */
    private final class Importer {

        private final Map<String, Category> categories = new HashMap<>();
        private final Map<String, Question> questions = new HashMap<>();
        private final Map<Long, Integer> chapterSort = new HashMap<>();
        /** 章节 id → 题型 → 已导入的题 id，用来算规则的候选池。 */
        private final Map<Long, Map<String, Set<Long>>> chapterPools = new HashMap<>();
        private final LocalDateTime now = TimeUtil.nowUtc(clock);
        private int questionTotal;
        private int paperTotal;
        private String file;

        Result importAll(List<Resource> files) {
            for (Resource r : files) {
                file = r.getFilename();
                SeedFile seed;
                try (InputStream in = r.getInputStream()) {
                    seed = json.readValue(in, SeedFile.class);
                } catch (IOException e) {
                    throw fail("JSON 格式错误：" + e.getMessage());
                }
                if (seed.categories() != null) {
                    int sort = 0;
                    for (SeedCategory c : seed.categories()) {
                        addCategory(c, null, 1, sort++);
                    }
                }
                if (seed.questions() != null) {
                    int i = 0;
                    for (SeedQuestion q : seed.questions()) {
                        addQuestion(q, ++i);
                    }
                }
                if (seed.papers() != null) {
                    for (SeedPaper p : seed.papers()) {
                        addPaper(p);
                    }
                }
            }
            return new Result(files.size(), categories.size(), questionTotal, paperTotal);
        }

        private void addCategory(SeedCategory c, Category parent, int level, int sort) {
            if (c.key() == null || !KEY.matcher(c.key()).matches()) {
                throw fail("分类 key 只能用小写字母、数字和连字符：" + c.key());
            }
            if (categories.containsKey(c.key())) {
                throw fail("分类 key 重复：" + c.key());
            }
            if (c.name() == null || c.name().isBlank() || c.name().length() > 50) {
                throw fail("分类 " + c.key() + " 的名称不能为空且不超过 50 字");
            }
            if (level > 3) {
                throw fail("分类最多 3 级：" + c.key());
            }
            Category entity = new Category()
                    .setParentId(parent == null ? null : parent.getId())
                    .setName(c.name().strip())
                    .setLevel(level)
                    .setSort(sort)
                    .setCreatedAt(now);
            categoryMapper.insert(entity);
            categories.put(c.key(), entity);
            byId.put(entity.getId(), entity);
            if (c.children() != null) {
                int childSort = 0;
                for (SeedCategory child : c.children()) {
                    addCategory(child, entity, level + 1, childSort++);
                }
            }
        }

        private void addQuestion(SeedQuestion q, int index) {
            String where = "第 " + index + " 道题" + (q.ref() == null ? "" : "（ref=" + q.ref() + "）");
            Category chapter = categories.get(q.category());
            if (chapter == null) {
                throw fail(where + "：分类 " + q.category() + " 未定义（定义它的文件名要排在前面）");
            }
            if (chapter.getLevel() != 3) {
                throw fail(where + "：题目只能挂在三级分类上，" + q.category() + " 是 " + chapter.getLevel() + " 级");
            }
            QuestionType type;
            try {
                type = QuestionType.valueOf(String.valueOf(q.type()));
            } catch (IllegalArgumentException e) {
                throw fail(where + "：题型不正确 " + q.type());
            }
            if (q.stem() == null || q.stem().isBlank()) {
                throw fail(where + "：题干不能为空");
            }
            if (q.analysis() == null || q.analysis().isBlank()) {
                throw fail(where + "：每道题都要有解析");
            }
            List<OptionItem> options = new ArrayList<>();
            if (type.isChoice()) {
                List<String> texts = q.options() == null ? List.of() : q.options();
                for (int i = 0; i < texts.size(); i++) {
                    options.add(new OptionItem(QuestionRules.keyAt(i), texts.get(i)));
                }
            } else if (q.options() != null && !q.options().isEmpty()) {
                throw fail(where + "：判断题和填空题不写 options");
            }
            List<String> correct = null;
            List<List<String>> accepted = null;
            try {
                if (type == QuestionType.FILL) {
                    accepted = json.convertValue(q.answer(), NESTED_LIST);
                } else {
                    correct = json.convertValue(q.answer(), STRING_LIST);
                }
            } catch (IllegalArgumentException e) {
                throw fail(where + "：answer 格式不正确");
            }
            String error = QuestionRules.check(type, options, correct, accepted);
            if (error != null) {
                throw fail(where + "：" + error);
            }
            int difficulty = q.difficulty() == null ? 3 : q.difficulty();
            if (difficulty < 1 || difficulty > 5) {
                throw fail(where + "：难度只能是 1–5");
            }
            if (q.ref() != null && questions.containsKey(q.ref())) {
                throw fail(where + "：ref 重复");
            }
            int sort = chapterSort.merge(chapter.getId(), 1, Integer::sum);
            Question entity = new Question()
                    .setCategoryId(chapter.getId())
                    .setType(type.name())
                    .setStem(q.stem())
                    .setOptionsJson(type.isChoice() ? JsonUtil.write(options) : null)
                    .setAnswerJson(JsonUtil.write(type == QuestionType.FILL ? accepted : correct))
                    .setFillOptions(type == QuestionType.FILL
                            ? JsonUtil.write(q.fillOptions() == null ? FillOptions.DEFAULT : q.fillOptions()) : null)
                    .setAnalysis(q.analysis())
                    .setDifficulty(difficulty)
                    .setTags(q.tags() == null || q.tags().isEmpty() ? null
                            : String.join(",", q.tags().stream().map(String::strip).toList()))
                    .setImageUrl(q.imageUrl())
                    .setSourceNote(q.source() == null || q.source().isBlank() ? "原创" : q.source())
                    .setStatus("PUBLISHED")
                    .setVersion(1)
                    .setSort(sort)
                    .setCreatedAt(now)
                    .setUpdatedAt(now);
            questionMapper.insert(entity);
            if (q.ref() != null) {
                questions.put(q.ref(), entity);
            }
            chapterPools.computeIfAbsent(chapter.getId(), k -> new HashMap<>())
                    .computeIfAbsent(type.name(), k -> new HashSet<>()).add(entity.getId());
            questionTotal++;
        }

        private void addPaper(SeedPaper p) {
            String where = "试卷《" + p.title() + "》";
            if (p.title() == null || p.title().isBlank()) {
                throw fail("试卷标题不能为空");
            }
            Category exam = categories.get(p.category());
            if (exam == null || exam.getLevel() != 1) {
                throw fail(where + "：category 必须是已定义的一级分类，实际为 " + p.category());
            }
            if (p.durationMin() == null || p.durationMin() <= 0 || p.passScore() == null || p.passScore() < 0) {
                throw fail(where + "：durationMin 必须大于 0，passScore 不能为空");
            }
            String mode = String.valueOf(p.mode()).toUpperCase(Locale.ROOT);
            List<PaperQuestion> fixed = new ArrayList<>();
            List<PaperRule> rules = new ArrayList<>();
            int total = 0;
            if ("FIXED".equals(mode)) {
                if (p.questions() == null || p.questions().isEmpty()) {
                    throw fail(where + "：固定卷至少要有 1 道题");
                }
                int sort = 0;
                Set<Long> seen = new HashSet<>();
                for (SeedPaperQuestion pq : p.questions()) {
                    Question q = questions.get(pq.ref());
                    if (q == null) {
                        throw fail(where + "：引用的题目 ref=" + pq.ref() + " 不存在");
                    }
                    if (!exam.getId().equals(rootOf(byId(q.getCategoryId())))) {
                        throw fail(where + "：题目 ref=" + pq.ref() + " 不属于试卷所在的考试 " + p.category());
                    }
                    if (!seen.add(q.getId())) {
                        throw fail(where + "：题目 ref=" + pq.ref() + " 重复");
                    }
                    if (pq.score() == null || pq.score() <= 0) {
                        throw fail(where + "：题目 ref=" + pq.ref() + " 的分值必须大于 0");
                    }
                    fixed.add(new PaperQuestion().setQuestionId(q.getId()).setScore(pq.score()).setSort(sort++));
                    total += pq.score();
                }
            } else if ("RULE".equals(mode)) {
                if (p.rules() == null || p.rules().isEmpty()) {
                    throw fail(where + "：规则卷至少要有 1 条规则");
                }
                int sort = 0;
                List<RuleDrawer.Rule> drawRules = new ArrayList<>();
                for (SeedRule r : p.rules()) {
                    Category c = categories.get(r.category());
                    if (c == null) {
                        throw fail(where + "：规则分类 " + r.category() + " 未定义");
                    }
                    if (!exam.getId().equals(rootOf(c))) {
                        throw fail(where + "：规则分类 " + r.category() + " 不属于试卷所在的考试 " + p.category());
                    }
                    String type;
                    try {
                        type = QuestionType.valueOf(String.valueOf(r.type())).name();
                    } catch (IllegalArgumentException e) {
                        throw fail(where + "：规则题型不正确 " + r.type());
                    }
                    if (r.count() == null || r.count() <= 0 || r.score() == null || r.score() <= 0) {
                        throw fail(where + "：规则的 count 和 score 必须大于 0");
                    }
                    drawRules.add(new RuleDrawer.Rule(sort, c.getId(), type, r.count(), poolOf(c, type)));
                    rules.add(new PaperRule().setCategoryId(c.getId()).setType(type).setCount(r.count())
                            .setScore(r.score()).setSort(sort++));
                    total += r.count() * r.score();
                }
                List<RuleDrawer.Gap> gaps = RuleDrawer.gaps(drawRules);
                if (!gaps.isEmpty()) {
                    RuleDrawer.Gap g = gaps.get(0);
                    throw fail(where + "：第 " + (g.index() + 1) + " 条规则（" + p.rules().get(g.index()).category()
                            + " / " + g.type() + "）范围内只有 " + g.available() + " 道，连同被它包含的规则共需 "
                            + g.need() + " 道");
                }
            } else {
                throw fail(where + "：mode 只能是 FIXED 或 RULE");
            }
            if (p.passScore() > total) {
                throw fail(where + "：及格分 " + p.passScore() + " 超过总分 " + total);
            }
            ExamPaper paper = new ExamPaper()
                    .setTitle(p.title().strip())
                    .setCategoryId(exam.getId())
                    .setDescription(p.description())
                    .setMode(mode)
                    .setDurationMin(p.durationMin())
                    .setTotalScore(total)
                    .setPassScore(p.passScore())
                    .setStatus("ONLINE")
                    .setCreatedAt(now)
                    .setUpdatedAt(now);
            paperMapper.insert(paper);
            for (PaperQuestion pq : fixed) {
                paperQuestionMapper.insert(pq.setPaperId(paper.getId()));
            }
            for (PaperRule r : rules) {
                paperRuleMapper.insert(r.setPaperId(paper.getId()));
            }
            paperTotal++;
        }

        private final Map<Long, Category> byId = new HashMap<>();

        private Category byId(Long id) {
            return byId.get(id);
        }

        private Long rootOf(Category c) {
            Category cur = c;
            while (cur.getParentId() != null) {
                cur = byId.get(cur.getParentId());
            }
            return cur.getId();
        }

        /** 分类子树里某题型已导入的题 id。 */
        private Set<Long> poolOf(Category c, String type) {
            Set<Long> pool = new HashSet<>();
            if (c.getLevel() == 3) {
                pool.addAll(chapterPools.getOrDefault(c.getId(), Map.of()).getOrDefault(type, Set.of()));
                return pool;
            }
            for (Category child : byId.values()) {
                if (c.getId().equals(child.getParentId())) {
                    pool.addAll(poolOf(child, type));
                }
            }
            return pool;
        }

        private IllegalStateException fail(String message) {
            return new IllegalStateException("示例题库 " + file + " 有误：" + message);
        }
    }
}
