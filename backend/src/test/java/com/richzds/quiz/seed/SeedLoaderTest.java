package com.richzds.quiz.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.transaction.support.TransactionTemplate;

import com.richzds.quiz.config.AppProperties;
import com.richzds.quiz.mapper.ExamPaperMapper;
import com.richzds.quiz.mapper.PaperQuestionMapper;
import com.richzds.quiz.mapper.PaperRuleMapper;
import com.richzds.quiz.support.ApiTestBase;

/** 示例题库导入：test 配置读 src/test/resources/seed-sample/00-sample.json（3 个分类、8 道题、2 份卷）。 */
class SeedLoaderTest extends ApiTestBase {

    @Autowired
    SeedLoader seedLoader;
    @Autowired
    AppProperties props;
    @Autowired
    ExamPaperMapper paperMapper;
    @Autowired
    PaperQuestionMapper paperQuestionMapper;
    @Autowired
    PaperRuleMapper paperRuleMapper;
    @Autowired
    TransactionTemplate tx;

    /** 正式题库（main 资源 seed/，产品经理维护）整套导入不报错，题量和卷数与交付一致。 */
    @Test
    void productionSeedImportsCompletely() {
        AppProperties prod = new AppProperties(props.jwt(), props.admin(),
                new AppProperties.Seed(true, "classpath:seed/"), props.upload(), props.release(), props.cors());
        SeedLoader loader = new SeedLoader(categoryMapper, questionMapper, paperMapper, paperQuestionMapper,
                paperRuleMapper, tx, prod, clock);
        SeedLoader.Result r = loader.load();
        assertThat(r.questions()).isEqualTo(396);
        assertThat(r.papers()).isEqualTo(6);
        assertThat(count("question")).isEqualTo(396);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM category WHERE level = 1", Long.class)).isEqualTo(3);
    }

    @Test
    void importsCategoriesQuestionsAndPapers() throws Exception {
        SeedLoader.Result r = seedLoader.load();
        assertThat(r).isEqualTo(new SeedLoader.Result(1, 3, 8, 2));
        assertThat(count("category")).isEqualTo(r.categories());
        assertThat(count("question")).isEqualTo(r.questions());
        assertThat(count("exam_paper")).isEqualTo(r.papers());
        assertThat(r.questions()).isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM question WHERE status <> 'PUBLISHED'", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exam_paper WHERE status <> 'ONLINE'", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM question WHERE category_id NOT IN "
                + "(SELECT id FROM category WHERE level = 3)", Long.class)).as("题只挂在章节上").isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM question WHERE analysis IS NULL OR source_note IS NULL",
                Long.class)).isZero();

        // 固定卷总分 = 各题分值之和；规则卷总分 = Σ count × score
        List<Map<String, Object>> fixed = jdbc.queryForList("SELECT p.total_score, SUM(pq.score) AS s FROM exam_paper p "
                + "JOIN paper_question pq ON pq.paper_id = p.id GROUP BY p.id, p.total_score");
        fixed.forEach(row -> assertThat(((Number) row.get("total_score")).intValue())
                .isEqualTo(((Number) row.get("s")).intValue()));
        List<Map<String, Object>> rule = jdbc.queryForList("SELECT p.total_score, SUM(r.count * r.score) AS s "
                + "FROM exam_paper p JOIN paper_rule r ON r.paper_id = p.id GROUP BY p.id, p.total_score");
        rule.forEach(row -> assertThat(((Number) row.get("total_score")).intValue())
                .isEqualTo(((Number) row.get("s")).intValue()));
        assertThat(fixed.size() + rule.size()).isEqualTo(r.papers());
        assertThat(jdbc.queryForList("SELECT shuffle_options FROM exam_paper ORDER BY id", Boolean.class))
                .as("样例里规则卷写了 shuffleOptions: true，固定卷没写").containsExactly(false, true);

        // 导入的题能正常出现在接口里，代码块已渲染
        Login u = register("seed1");
        JsonNode roots = data(call(get("/api/categories", u)));
        assertThat(roots.size()).isPositive();
        Long codeQuestion = jdbc.queryForObject(
                "SELECT MIN(id) FROM question WHERE stem LIKE '%```java%'", Long.class);
        JsonNode detail = data(call(get("/api/questions/" + codeQuestion, u)));
        assertThat(detail.get("stemHtml").asText()).contains("<pre style=\"font-family:");
        assertThat(detail.get("acceptedAnswers").toString()).isEqualTo("[[\"12\"]]");
    }

    @Test
    void runnerSkipsWhenCategoriesExist() throws Exception {
        category(null, "已有分类", 0);
        seedLoader.run(new DefaultApplicationArguments());
        assertThat(count("category")).isEqualTo(1);
        assertThat(count("question")).isZero();
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
