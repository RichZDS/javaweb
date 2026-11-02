package com.richzds.quiz.exam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 规则卷抽题：每条规则从自己的候选池（某分类子树里某题型的已发布题）抽 count 道，
 * 同一道题不会被两条规则重复抽到。
 *
 * <p>同一题型下，各规则的候选池是分类树的子树，要么互相包含要么不相交（层状族）。
 * 对层状族，“每个池子都装得下所有被它包含的规则的总题量”就是能抽成的充要条件；
 * 按池子从小到大依次抽，内层先占用、外层用剩下的，满足条件时一定能抽成。
 */
public final class RuleDrawer {

    private RuleDrawer() {
    }

    /** 一条规则：index 是它在试卷里的序号，pool 是候选题 id。 */
    public record Rule(int index, Long categoryId, String type, int count, Set<Long> pool) {
    }

    /** 题量缺口：need 是这个池子里需要的总题量（含被它包含的其他规则），available 是池子大小。 */
    public record Gap(int index, Long categoryId, String type, int need, int available) {
    }

    /** 检查能否抽成；返回全部缺口，空列表表示可以。 */
    public static List<Gap> gaps(List<Rule> rules) {
        List<Gap> gaps = new ArrayList<>();
        for (Rule r : rules) {
            int need = 0;
            for (Rule s : rules) {
                // 被 r 的池子包含的规则（含 r 自己和池子相同的规则）都只能从 r 的池子里抽
                if (s.type().equals(r.type()) && r.pool().containsAll(s.pool())) {
                    need += s.count();
                }
            }
            if (need > r.pool().size()) {
                gaps.add(new Gap(r.index(), r.categoryId(), r.type(), need, r.pool().size()));
            }
        }
        return gaps;
    }

    /**
     * 抽题，返回 规则序号 → 抽到的题 id（每条规则内部已随机打乱）。
     *
     * @throws IllegalStateException 有缺口时（调用方应先用 {@link #gaps} 检查）
     */
    public static Map<Integer, List<Long>> draw(List<Rule> rules, Random random) {
        List<Rule> order = new ArrayList<>(rules);
        order.sort(Comparator.comparingInt((Rule r) -> r.pool().size()).thenComparingInt(Rule::index));
        Set<Long> used = new HashSet<>();
        Map<Integer, List<Long>> picked = new LinkedHashMap<>();
        for (Rule r : order) {
            List<Long> candidates = new ArrayList<>();
            for (Long id : r.pool()) {
                if (!used.contains(id)) {
                    candidates.add(id);
                }
            }
            if (candidates.size() < r.count()) {
                throw new IllegalStateException("规则 " + r.index() + " 题量不足");
            }
            candidates.sort(null);
            Collections.shuffle(candidates, random);
            List<Long> chosen = new ArrayList<>(candidates.subList(0, r.count()));
            used.addAll(chosen);
            picked.put(r.index(), chosen);
        }
        return picked;
    }
}
