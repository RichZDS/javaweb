package com.richzds.quiz.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import com.richzds.quiz.exam.RuleDrawer.Gap;
import com.richzds.quiz.exam.RuleDrawer.Rule;

class RuleDrawerTest {

    private static Set<Long> ids(long from, long to) {
        return LongStream.rangeClosed(from, to).boxed().collect(Collectors.toSet());
    }

    @Test
    void nestedRulesNeedTheirSumInTheOuterPool() {
        // 外层：整个考试 1–10；内层：某章节 1–4
        Rule outer = new Rule(0, 100L, "SINGLE", 7, ids(1, 10));
        Rule inner = new Rule(1, 12L, "SINGLE", 4, ids(1, 4));
        assertThat(RuleDrawer.gaps(List.of(outer, inner))).as("4 + 7 > 10").hasSize(1);
        Gap g = RuleDrawer.gaps(List.of(outer, inner)).get(0);
        assertThat(g.index()).isZero();
        assertThat(g.need()).isEqualTo(11);
        assertThat(g.available()).isEqualTo(10);

        Rule outer6 = new Rule(0, 100L, "SINGLE", 6, ids(1, 10));
        assertThat(RuleDrawer.gaps(List.of(outer6, inner))).isEmpty();
    }

    @RepeatedTest(20)
    void drawNeverRepeatsAQuestionAcrossRules() {
        Rule outer = new Rule(0, 100L, "SINGLE", 6, ids(1, 10));
        Rule inner = new Rule(1, 12L, "SINGLE", 4, ids(1, 4));
        Rule other = new Rule(2, 13L, "JUDGE", 2, ids(20, 21));
        Map<Integer, List<Long>> picked = RuleDrawer.draw(List.of(outer, inner, other), new Random());
        assertThat(picked.get(0)).hasSize(6);
        assertThat(picked.get(1)).hasSize(4).allMatch(id -> id <= 4);
        assertThat(picked.get(2)).containsExactlyInAnyOrder(20L, 21L);
        Set<Long> all = new HashSet<>();
        picked.values().forEach(all::addAll);
        assertThat(all).hasSize(12);
    }

    @Test
    void differentTypesDoNotCompete() {
        Rule a = new Rule(0, 1L, "SINGLE", 3, ids(1, 3));
        Rule b = new Rule(1, 1L, "JUDGE", 3, ids(4, 6));
        assertThat(RuleDrawer.gaps(List.of(a, b))).isEmpty();
    }

    @Test
    void sameCategoryTwiceSharesThePool() {
        Rule a = new Rule(0, 1L, "SINGLE", 3, ids(1, 5));
        Rule b = new Rule(1, 1L, "SINGLE", 3, ids(1, 5));
        assertThat(RuleDrawer.gaps(List.of(a, b))).hasSize(2);
        assertThatThrownBy(() -> RuleDrawer.draw(List.of(a, b), new Random())).isInstanceOf(IllegalStateException.class);
    }
}
