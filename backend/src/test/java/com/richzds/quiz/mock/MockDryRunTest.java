package com.richzds.quiz.mock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 预演（app.mock.dry-run=true）只读数据、打印计划，不写库，也不需要学员密码。 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:quiz_mock_dry;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "app.seed.enabled=true",
        "app.seed.location=classpath:seed/",
        "app.mock.enabled=true",
        "app.mock.dry-run=true",
        "app.mock.exit=false",
        "app.mock.location=classpath:mock-test/",
        "app.mock.until=2026-07-11T12:00:00Z"})
@ActiveProfiles("test")
class MockDryRunTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void dryRunWritesNothing() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE role <> 'ADMIN'", Long.class)).isZero();
        for (String t : new String[] {"practice_session", "answer_record", "exam_record", "favorite",
                "question_feedback"}) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + t, Long.class)).as(t).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM question WHERE stem LIKE 'ArrayList 默认%'", Long.class))
                .as("管理员也没有加题").isZero();
    }
}
