package com.richzds.quiz.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

class OptionShuffleTest {

    private static final List<OptionItem> OPTIONS = List.of(new OptionItem("A", "甲"), new OptionItem("B", "乙"),
            new OptionItem("C", "丙"), new OptionItem("D", "丁"));

    @Test
    void mapsBothWays() {
        OptionShuffle s = OptionShuffle.of(List.of("C", "A", "D", "B"), OPTIONS);
        assertThat(s.mapping()).containsExactly(Map.entry("A", "C"), Map.entry("B", "A"),
                Map.entry("C", "D"), Map.entry("D", "B"));
        assertThat(s.displayOptions(OPTIONS)).containsExactly(new OptionItem("A", "丙"), new OptionItem("B", "甲"),
                new OptionItem("C", "丁"), new OptionItem("D", "乙"));
        assertThat(s.toOriginal(List.of("a", " B "))).containsExactly("C", "A");
        assertThat(s.toDisplay(List.of("C", "A"))).as("多选按显示标签升序").containsExactly("A", "B");
        assertThat(s.toDisplay(List.of("B"))).containsExactly("D");
    }

    @Test
    void unknownLabelsStayInvalid() {
        OptionShuffle s = OptionShuffle.of(List.of("B", "A", "C", "D"), OPTIONS);
        assertThat(s.toOriginal(List.of("E"))).containsExactly("E");
        assertThat(s.toDisplay(null)).isNull();
    }

    @Test
    void mismatchedOrderMeansNoShuffle() {
        assertThat(OptionShuffle.of(null, OPTIONS)).isNull();
        assertThat(OptionShuffle.of(List.of("A", "B", "C"), OPTIONS)).as("选项个数变了").isNull();
        assertThat(OptionShuffle.of(List.of("A", "A", "C", "D"), OPTIONS)).as("不是一个排列").isNull();
        assertThat(OptionShuffle.of(List.of("A", "B", "C", "E"), OPTIONS)).isNull();
    }

    @Test
    void randomOrderIsAPermutationAndVaries() {
        Random random = new Random(1);
        HashSet<List<String>> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            List<String> order = OptionShuffle.randomOrder(OPTIONS, random);
            assertThat(order).containsExactlyInAnyOrder("A", "B", "C", "D");
            seen.add(order);
        }
        assertThat(seen.size()).as("50 次里出现多种顺序").isGreaterThan(5);
    }
}
