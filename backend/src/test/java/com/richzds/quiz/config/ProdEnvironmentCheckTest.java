package com.richzds.quiz.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProdEnvironmentCheckTest {

    private final ProdEnvironmentCheck check = new ProdEnvironmentCheck();

    private static MockEnvironment prod() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        return env;
    }

    private static MockEnvironment complete() {
        return prod().withProperty("DB_URL", "jdbc:mysql://db:3306/quiz").withProperty("DB_USERNAME", "quiz")
                .withProperty("DB_PASSWORD", "x").withProperty("JWT_SECRET", "0123456789abcdef0123456789abcdef")
                .withProperty("ADMIN_PASSWORD", "y");
    }

    @Test
    void listsEveryMissingVariableAtOnce() {
        assertThatThrownBy(() -> check.postProcessEnvironment(prod(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_URL").hasMessageContaining("DB_USERNAME").hasMessageContaining("DB_PASSWORD")
                .hasMessageContaining("JWT_SECRET").hasMessageContaining("ADMIN_PASSWORD")
                .hasMessageContaining("deploy/.env.example");
    }

    @Test
    void rejectsShortJwtSecretAndBlankValues() {
        assertThatThrownBy(() -> check.postProcessEnvironment(complete().withProperty("JWT_SECRET", "short"), null))
                .hasMessageContaining("JWT_SECRET 只有 5 字节");
        assertThatThrownBy(() -> check.postProcessEnvironment(complete().withProperty("ADMIN_PASSWORD", " "), null))
                .hasMessageContaining("ADMIN_PASSWORD").hasMessageNotContaining("DB_URL");
    }

    @Test
    void passesWhenCompleteOrNotProd() {
        assertThatCode(() -> check.postProcessEnvironment(complete(), null)).doesNotThrowAnyException();
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");
        assertThatCode(() -> check.postProcessEnvironment(dev, null)).doesNotThrowAnyException();
    }
}
