package com.richzds.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.richzds.quiz.domain.AnswerKey;
import com.richzds.quiz.domain.FillOptions;
import com.richzds.quiz.domain.QuestionType;

class GradingServiceTest {

    private final GradingService grading = new GradingService();

    private static AnswerKey choice(QuestionType type, String... correct) {
        return new AnswerKey(type, List.of(correct), null, null);
    }

    private static AnswerKey fill(boolean ignoreCase, boolean ignoreSpaces, List<List<String>> accepted) {
        return new AnswerKey(QuestionType.FILL, null, accepted, new FillOptions(ignoreCase, ignoreSpaces));
    }

    @Test
    void singleChoiceMustMatchExactly() {
        AnswerKey key = choice(QuestionType.SINGLE, "B");
        assertThat(grading.grade(key, List.of("B"))).isTrue();
        assertThat(grading.grade(key, List.of(" b "))).as("大小写和空白不影响选项").isTrue();
        assertThat(grading.grade(key, List.of("A"))).isFalse();
        assertThat(grading.grade(key, List.of("A", "B"))).as("单选多给一个也错").isFalse();
        assertThat(grading.grade(key, List.of())).isFalse();
        assertThat(grading.grade(key, null)).isFalse();
    }

    @Test
    void judge() {
        AnswerKey key = choice(QuestionType.JUDGE, "F");
        assertThat(grading.grade(key, List.of("F"))).isTrue();
        assertThat(grading.grade(key, List.of("T"))).isFalse();
        assertThat(grading.grade(key, List.of())).isFalse();
    }

    @Test
    void multipleChoiceNeedsExactSet() {
        AnswerKey key = choice(QuestionType.MULTIPLE, "A", "C");
        assertThat(grading.grade(key, List.of("A", "C"))).isTrue();
        assertThat(grading.grade(key, List.of("C", "A"))).as("顺序无关").isTrue();
        assertThat(grading.grade(key, List.of("A"))).as("少选").isFalse();
        assertThat(grading.grade(key, List.of("A", "B", "C"))).as("多选").isFalse();
        assertThat(grading.grade(key, List.of("A", "B"))).as("错选").isFalse();
        assertThat(grading.grade(key, List.of())).as("空选").isFalse();
    }

    @Test
    void fillIsCaseAndSpaceSensitiveByDefault() {
        AnswerKey key = fill(false, false, List.of(List.of("int", "Integer"), List.of("5")));
        assertThat(grading.grade(key, List.of("int", "5"))).isTrue();
        assertThat(grading.grade(key, List.of("Integer", "5"))).as("命中任一可接受答案").isTrue();
        assertThat(grading.grade(key, List.of("  int\t", " 5 "))).as("首尾空白总是去掉").isTrue();
        assertThat(grading.grade(key, List.of("INT", "5"))).as("默认区分大小写").isFalse();
        assertThat(grading.grade(key, List.of("in t", "5"))).as("默认不去中间空格").isFalse();
        assertThat(grading.grade(key, List.of("int"))).as("少一空").isFalse();
        assertThat(grading.grade(key, List.of("int", "6"))).as("一空错就整题错").isFalse();
        assertThat(grading.grade(key, List.of("", "5"))).isFalse();
    }

    @Test
    void fillIgnoreCase() {
        AnswerKey key = fill(true, false, List.of(List.of("Object")));
        assertThat(grading.grade(key, List.of("object"))).isTrue();
        assertThat(grading.grade(key, List.of("OBJECT"))).isTrue();
        assertThat(grading.grade(key, List.of("Obj ect"))).isFalse();
    }

    @Test
    void fillIgnoreSpaces() {
        AnswerKey key = fill(false, true, List.of(List.of("a + b")));
        assertThat(grading.grade(key, List.of("a+b"))).isTrue();
        assertThat(grading.grade(key, List.of("a　+ b"))).as("全角空格、不换行空格也算空白").isTrue();
        assertThat(grading.grade(key, List.of("A+B"))).as("仍区分大小写").isFalse();
    }

    @Test
    void fillNormalizesNewlines() {
        AnswerKey key = fill(false, false, List.of(List.of("1\n2")));
        assertThat(grading.grade(key, List.of("1\r\n2"))).isTrue();
        assertThat(grading.grade(key, List.of("1\r2\n"))).isTrue();
    }

    @Test
    void fillWithNullBlankIsWrong() {
        AnswerKey key = fill(false, false, List.of(List.of("x")));
        assertThat(grading.grade(key, Arrays.asList((String) null))).isFalse();
    }

    @Test
    void normalizeChoiceSortsAndDeduplicates() {
        assertThat(grading.normalize(QuestionType.MULTIPLE, List.of("c", "A", "C", " "), 0))
                .containsExactly("A", "C");
    }

    @Test
    void normalizeFillPadsToBlankCount() {
        assertThat(grading.normalize(QuestionType.FILL, List.of("x"), 3)).containsExactly("x", "", "");
        assertThat(grading.normalize(QuestionType.FILL, List.of("a", "b", "c"), 2)).containsExactly("a", "b");
    }
}
