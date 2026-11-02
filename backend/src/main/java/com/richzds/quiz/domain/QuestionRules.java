package com.richzds.quiz.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 题目内容的结构校验（示例题库导入、后台录题、Excel 导入共用）。
 * 返回第一条错误的中文说明，没有错误时返回 null。
 */
public final class QuestionRules {

    public static final int MIN_OPTIONS = 2;
    public static final int MAX_OPTIONS = 6;

    private QuestionRules() {
    }

    /** 第 i 个选项的 key：A、B、C…… */
    public static String keyAt(int i) {
        return String.valueOf((char) ('A' + i));
    }

    public static String check(QuestionType type, List<OptionItem> options, List<String> correctAnswer,
                               List<List<String>> acceptedAnswers) {
        switch (type) {
            case SINGLE, MULTIPLE -> {
                if (options == null || options.size() < MIN_OPTIONS || options.size() > MAX_OPTIONS) {
                    return "选择题要有 2–6 个选项";
                }
                for (int i = 0; i < options.size(); i++) {
                    OptionItem o = options.get(i);
                    if (o == null || !keyAt(i).equals(o.key())) {
                        return "选项 key 必须按顺序是 A、B、C……";
                    }
                    if (o.text() == null || o.text().isBlank()) {
                        return "选项 " + keyAt(i) + " 的内容不能为空";
                    }
                }
                if (correctAnswer == null || correctAnswer.isEmpty()) {
                    return "缺少正确答案";
                }
                Set<String> keys = new HashSet<>();
                for (String k : correctAnswer) {
                    if (k == null || k.length() != 1 || k.charAt(0) < 'A' || k.charAt(0) >= 'A' + options.size()) {
                        return "正确答案 " + k + " 不是已有的选项";
                    }
                    if (!keys.add(k)) {
                        return "正确答案里有重复的选项 " + k;
                    }
                }
                if (type == QuestionType.SINGLE && keys.size() != 1) {
                    return "单选题只能有 1 个正确答案";
                }
                if (type == QuestionType.MULTIPLE && keys.size() < 2) {
                    return "多选题至少要有 2 个正确答案";
                }
            }
            case JUDGE -> {
                if (correctAnswer == null || correctAnswer.size() != 1
                        || !("T".equals(correctAnswer.get(0)) || "F".equals(correctAnswer.get(0)))) {
                    return "判断题答案只能是 [\"T\"] 或 [\"F\"]";
                }
            }
            case FILL -> {
                if (acceptedAnswers == null || acceptedAnswers.isEmpty()) {
                    return "填空题至少要有 1 个空";
                }
                for (int i = 0; i < acceptedAnswers.size(); i++) {
                    List<String> blank = acceptedAnswers.get(i);
                    if (blank == null || blank.isEmpty() || blank.stream().anyMatch(s -> s == null || s.isBlank())) {
                        return "第 " + (i + 1) + " 空的可接受答案不能为空";
                    }
                }
            }
            default -> {
                return "未知题型";
            }
        }
        return null;
    }
}
