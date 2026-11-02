package com.richzds.quiz.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.richzds.quiz.domain.AnswerKey;
import com.richzds.quiz.domain.FillOptions;
import com.richzds.quiz.domain.QuestionType;

/**
 * 判分的唯一实现（规则见 docs/api.md 第 1 节），练习提交和考试结算都调这里，前端不判分。
 */
@Service
public class GradingService {

    /** ignoreSpaces 时去掉的空白：普通空白、不换行空格、全角空格。 */
    private static final Pattern ALL_SPACES = Pattern.compile("[\\s\\u00A0\\u3000]+");

    /** 只有全对才返回 true；多选少选、错选、空选都算错。 */
    public boolean grade(AnswerKey key, List<String> userAnswer) {
        List<String> answer = userAnswer == null ? List.of() : userAnswer;
        return switch (key.type()) {
            case SINGLE, JUDGE, MULTIPLE -> gradeChoice(key, answer);
            case FILL -> gradeFill(key, answer);
        };
    }

    /**
     * 把作答整理成存库和回显用的形式：选择/判断去空白、转大写、去重并按字母排序（多选顺序无关）；
     * 填空只统一换行，按空数补齐或截断。
     */
    public List<String> normalize(QuestionType type, List<String> userAnswer, int blankCount) {
        List<String> answer = userAnswer == null ? List.of() : userAnswer;
        if (type == QuestionType.FILL) {
            List<String> out = new ArrayList<>(blankCount);
            for (int i = 0; i < blankCount; i++) {
                String v = i < answer.size() && answer.get(i) != null ? answer.get(i) : "";
                out.add(unifyNewlines(v));
            }
            return out;
        }
        return List.copyOf(choiceSet(answer));
    }

    private boolean gradeChoice(AnswerKey key, List<String> answer) {
        Set<String> expected = choiceSet(key.correctAnswer());
        Set<String> actual = choiceSet(answer);
        if (expected.isEmpty() || actual.isEmpty()) {
            return false;
        }
        if (key.type() != QuestionType.MULTIPLE && actual.size() != 1) {
            return false;
        }
        return expected.equals(actual);
    }

    private boolean gradeFill(AnswerKey key, List<String> answer) {
        List<List<String>> accepted = key.acceptedAnswers();
        if (accepted == null || accepted.isEmpty()) {
            return false;
        }
        FillOptions opts = key.fillOptions() == null ? FillOptions.DEFAULT : key.fillOptions();
        for (int i = 0; i < accepted.size(); i++) {
            String given = i < answer.size() ? answer.get(i) : null;
            if (!blankMatches(given, accepted.get(i), opts)) {
                return false;
            }
        }
        return true;
    }

    private boolean blankMatches(String given, List<String> candidates, FillOptions opts) {
        if (given == null || candidates == null) {
            return false;
        }
        String actual = normalizeBlank(given, opts);
        if (actual.isEmpty()) {
            return false;
        }
        for (String candidate : candidates) {
            if (candidate != null && actual.equals(normalizeBlank(candidate, opts))) {
                return true;
            }
        }
        return false;
    }

    /** 每空：去首尾空白、统一换行；按开关忽略大小写、去掉所有空白。 */
    static String normalizeBlank(String raw, FillOptions opts) {
        String s = unifyNewlines(raw).strip();
        if (opts.ignoreSpaces()) {
            s = ALL_SPACES.matcher(s).replaceAll("");
        }
        if (opts.ignoreCase()) {
            s = s.toLowerCase(Locale.ROOT);
        }
        return s;
    }

    private static String unifyNewlines(String s) {
        return s.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static Set<String> choiceSet(List<String> values) {
        Set<String> set = new TreeSet<>();
        if (values != null) {
            for (String v : values) {
                if (v != null && !v.isBlank()) {
                    set.add(v.strip().toUpperCase(Locale.ROOT));
                }
            }
        }
        return set;
    }
}
