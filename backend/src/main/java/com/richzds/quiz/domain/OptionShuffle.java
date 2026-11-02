package com.richzds.quiz.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * 某一场（练习或考试）里一道选择题的选项显示顺序。
 * {@code order} 是按显示顺序排的原标签，例如 [C, A, D, B] 表示显示的 A 是原来的 C。
 * 客户端看到、提交的都是显示标签；库里存、判分用的都是原标签。
 */
public final class OptionShuffle {

    private final List<String> order;
    private final Map<String, String> displayToOriginal = new LinkedHashMap<>();
    private final Map<String, String> originalToDisplay = new HashMap<>();

    private OptionShuffle(List<String> order) {
        this.order = List.copyOf(order);
        for (int i = 0; i < order.size(); i++) {
            String display = QuestionRules.keyAt(i);
            displayToOriginal.put(display, order.get(i));
            originalToDisplay.put(order.get(i), display);
        }
    }

    /** 随机打乱选项（只用于单选、多选）。 */
    public static List<String> randomOrder(List<OptionItem> options, Random random) {
        List<String> keys = new ArrayList<>(options.stream().map(OptionItem::key).toList());
        Collections.shuffle(keys, random);
        return keys;
    }

    /**
     * 按存下的顺序建映射；顺序和当前选项对不上（例如开练习后管理员改了选项个数）时返回 null，表示按原顺序显示。
     */
    public static OptionShuffle of(List<String> order, List<OptionItem> options) {
        if (order == null || options == null || order.size() != options.size()) {
            return null;
        }
        HashSet<String> keys = new HashSet<>(options.stream().map(OptionItem::key).toList());
        if (!keys.equals(new HashSet<>(order)) || keys.size() != order.size()) {
            return null;
        }
        return new OptionShuffle(order);
    }

    /** 按显示顺序重排并重新标 A、B、C……，内容来自对应的原选项。 */
    public List<OptionItem> displayOptions(List<OptionItem> original) {
        Map<String, String> textByKey = new HashMap<>();
        original.forEach(o -> textByKey.put(o.key(), o.text()));
        List<OptionItem> out = new ArrayList<>(order.size());
        for (int i = 0; i < order.size(); i++) {
            out.add(new OptionItem(QuestionRules.keyAt(i), textByKey.get(order.get(i))));
        }
        return out;
    }

    /** 显示标签 → 原标签；不认识的标签原样保留（它在原标签里同样不存在，照常判错）。 */
    public List<String> toOriginal(List<String> display) {
        return display == null ? null : display.stream()
                .map(k -> k == null ? null : displayToOriginal.getOrDefault(k.strip().toUpperCase(Locale.ROOT), k))
                .toList();
    }

    /** 原标签 → 显示标签，按显示顺序排好（多选时客户端看到的是 A、C 这样的升序）。 */
    public List<String> toDisplay(List<String> original) {
        if (original == null) {
            return null;
        }
        return original.stream().map(k -> originalToDisplay.getOrDefault(k, k)).sorted().toList();
    }

    /** 接口里的 optionMapping：显示标签 → 原标签。 */
    public Map<String, String> mapping() {
        return displayToOriginal;
    }

    public List<String> order() {
        return order;
    }
}
