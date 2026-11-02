package com.richzds.quiz.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import com.richzds.quiz.domain.FillOptions;

/** 题目的对外表示（docs/api.md 第 1、3 节）。 */
public final class QuestionDtos {

    private QuestionDtos() {
    }

    public record OptionView(String key, String text, String html) {
    }

    /** 学员看到的题（不含答案）。 */
    public record QuestionView(Long id, Long categoryId, String categoryPath, String type, Integer difficulty,
                               List<String> tags, String stem, String stemHtml, List<OptionView> options,
                               int blankCount, String imageUrl, boolean favorited, String note) {
    }

    /** 交卷或提交后附带的答案部分；选择/判断用 correctAnswer，填空用 acceptedAnswers，另一个为 null。 */
    public record AnswerView(List<String> correctAnswer, List<List<String>> acceptedAnswers,
                             FillOptions fillOptions, String analysis, String analysisHtml) {
    }

    /** 题目详情 = 题目字段 + 答案字段，平铺在同一个对象里。 */
    public record QuestionDetail(@JsonUnwrapped QuestionView question, @JsonUnwrapped AnswerView answer) {
    }

    public record SearchItem(Long id, Long categoryId, String categoryPath, String type, String stemPreview) {
    }

    public record FeedbackRequest(
            @NotNull(message = "不能为空")
            @Pattern(regexp = "ANSWER_WRONG|STEM_WRONG|ANALYSIS_UNCLEAR|OTHER", message = "纠错类型不正确")
            String type,
            @NotBlank(message = "不能为空") @Size(max = 500, message = "最多 500 字") String content) {
    }

    public record FeedbackView(Long id, Long questionId, String type, String content, String status,
                               String reply, Instant createdAt, Instant handledAt) {
    }
}
