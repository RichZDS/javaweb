package com.richzds.quiz.service;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;

import com.richzds.quiz.common.JsonUtil;
import com.richzds.quiz.domain.AnswerKey;
import com.richzds.quiz.domain.OptionItem;
import com.richzds.quiz.domain.QuestionType;
import com.richzds.quiz.dto.QuestionDtos.AnswerView;
import com.richzds.quiz.dto.QuestionDtos.OptionView;
import com.richzds.quiz.dto.QuestionDtos.QuestionView;
import com.richzds.quiz.entity.Favorite;
import com.richzds.quiz.entity.Question;
import com.richzds.quiz.richtext.RichTextRenderer;

/** 把题目实体组装成接口里的题目 / 答案对象，负责 Markdown 渲染和判断题固定选项。 */
@Component
public class QuestionAssembler {

    public static final int PREVIEW_CHARS = 80;

    /** 判断题的固定选项。 */
    public static final List<OptionItem> JUDGE_OPTIONS =
            List.of(new OptionItem("T", "正确"), new OptionItem("F", "错误"));

    private static final TypeReference<List<OptionItem>> OPTION_LIST = new TypeReference<>() { };

    private final RichTextRenderer renderer;

    public QuestionAssembler(RichTextRenderer renderer) {
        this.renderer = renderer;
    }

    public QuestionView view(Question q, CategoryTree tree, Favorite favorite) {
        AnswerKey key = AnswerKey.of(q);
        return new QuestionView(q.getId(), q.getCategoryId(), tree.path(q.getCategoryId()), q.getType(),
                q.getDifficulty(), tags(q.getTags()), q.getStem(), renderer.toHtml(q.getStem()),
                optionViews(optionItems(q)), key.blankCount(), q.getImageUrl(),
                favorite != null, favorite == null ? null : favorite.getNote());
    }

    /** 同一道题换一套选项（选项打乱后用）。 */
    public static QuestionView withOptions(QuestionView v, List<OptionView> options) {
        return new QuestionView(v.id(), v.categoryId(), v.categoryPath(), v.type(), v.difficulty(), v.tags(), v.stem(),
                v.stemHtml(), options, v.blankCount(), v.imageUrl(), v.favorited(), v.note());
    }

    /** 同一份答案换成新标签的正确答案（选项打乱后用）。 */
    public static AnswerView withCorrectAnswer(AnswerView a, List<String> correctAnswer) {
        return new AnswerView(correctAnswer, a.acceptedAnswers(), a.fillOptions(), a.analysis(), a.analysisHtml());
    }

    public AnswerView answer(Question q) {
        return answer(AnswerKey.of(q), q.getAnalysis());
    }

    public AnswerView answer(AnswerKey key, String analysis) {
        boolean fill = key.type() == QuestionType.FILL;
        return new AnswerView(fill ? null : key.correctAnswer(), fill ? key.acceptedAnswers() : null,
                fill ? key.fillOptions() : null, analysis, renderer.toHtml(analysis));
    }

    /** 选项原文；判断题固定 T/F，填空题为空。 */
    public List<OptionItem> optionItems(Question q) {
        QuestionType type = QuestionType.valueOf(q.getType());
        if (type == QuestionType.JUDGE) {
            return JUDGE_OPTIONS;
        }
        if (type == QuestionType.FILL) {
            return List.of();
        }
        List<OptionItem> items = JsonUtil.read(q.getOptionsJson(), OPTION_LIST);
        return items == null ? List.of() : items;
    }

    public List<OptionView> optionViews(List<OptionItem> items) {
        return items.stream().map(o -> new OptionView(o.key(), o.text(), renderer.toHtml(o.text()))).toList();
    }

    public String preview(String stem) {
        return renderer.preview(stem, PREVIEW_CHARS);
    }

    public String html(String markdown) {
        return renderer.toHtml(markdown);
    }

    public static List<String> tags(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
