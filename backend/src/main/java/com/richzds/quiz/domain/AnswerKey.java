package com.richzds.quiz.domain;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;

import com.richzds.quiz.common.JsonUtil;
import com.richzds.quiz.entity.Question;

/**
 * 一道题的标准答案。选择和判断用 correctAnswer，填空用 acceptedAnswers（每空可接受的写法），
 * 另一个为 null。
 */
public record AnswerKey(QuestionType type,
                        List<String> correctAnswer,
                        List<List<String>> acceptedAnswers,
                        FillOptions fillOptions) {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private static final TypeReference<List<List<String>>> NESTED_LIST = new TypeReference<>() { };

    /** 从题目实体的 answer_json / fill_options 解析。 */
    public static AnswerKey of(Question q) {
        QuestionType type = QuestionType.valueOf(q.getType());
        if (type == QuestionType.FILL) {
            List<List<String>> accepted = JsonUtil.read(q.getAnswerJson(), NESTED_LIST);
            FillOptions opts = JsonUtil.read(q.getFillOptions(), FillOptions.class);
            return new AnswerKey(type, null, accepted == null ? List.of() : accepted,
                    opts == null ? FillOptions.DEFAULT : opts);
        }
        List<String> correct = JsonUtil.read(q.getAnswerJson(), STRING_LIST);
        return new AnswerKey(type, correct == null ? List.of() : correct, null, null);
    }

    /** 写回 answer_json 的内容。 */
    public String answerJson() {
        return JsonUtil.write(type == QuestionType.FILL ? acceptedAnswers : correctAnswer);
    }

    public int blankCount() {
        return type == QuestionType.FILL && acceptedAnswers != null ? acceptedAnswers.size() : 0;
    }
}
