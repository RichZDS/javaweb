package com.richzds.quiz.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import com.richzds.quiz.dto.QuestionDtos.AnswerView;
import com.richzds.quiz.dto.QuestionDtos.QuestionView;

/** 练习（docs/api.md 第 4 节）。 */
public final class PracticeDtos {

    private PracticeDtos() {
    }

    public record StartRequest(
            @NotNull(message = "不能为空")
            @Pattern(regexp = "RANDOM|SEQUENTIAL|WRONG|FAVORITE|DAILY|TAG|RETRY", message = "练习模式不正确")
            String mode,
            @Size(max = 100, message = "分类太多") List<@NotNull Long> categoryIds,
            Integer count,
            List<@NotNull @Pattern(regexp = "SINGLE|MULTIPLE|JUDGE|FILL", message = "题型不正确") String> types,
            @Pattern(regexp = "ALL|UNDONE|WRONG_EVER", message = "范围不正确") String scope,
            Boolean reciteMode,
            Boolean acceptFewer,
            @Size(max = 50, message = "标签太长") String tag,
            Long fromSessionId,
            Long fromExamRecordId,
            Boolean shuffleOptions) {
    }

    public record SessionView(Long id, String mode, boolean reciteMode, boolean shuffleOptions, String status,
                              int questionCount,
                              int answeredCount, int correctCount, int currentIndex, Instant startedAt,
                              Instant completedAt, LocalDate dailyDate, List<SessionQuestion> questions) {
    }

    /**
     * 练习里的一道题：题目字段 + 作答情况；提交后（或背题模式）再平铺答案字段。
     * 打乱了选项时，选项、userAnswer、correctAnswer 都用新标签，optionMapping 是新标签 → 原标签，否则为 null。
     */
    public record SessionQuestion(@JsonUnwrapped QuestionView question, boolean submitted, List<String> userAnswer,
                                  Boolean correct, Map<String, String> optionMapping,
                                  @JsonUnwrapped AnswerView answer) {
    }

    public record SubmitRequest(
            @NotNull(message = "不能为空") Long questionId,
            @NotNull(message = "不能为空") @Size(max = 20, message = "答案项太多")
            List<@Size(max = 500, message = "答案太长") String> userAnswer,
            @Min(value = 0, message = "不能为负") @Max(value = 86400, message = "太大") Integer timeSpentSec) {
    }

    public record SubmitResult(Long questionId, boolean correct, List<String> userAnswer, List<String> correctAnswer,
                               List<List<String>> acceptedAnswers, String analysisHtml, boolean alreadySubmitted,
                               Map<String, String> optionMapping) {
    }

    public record ProgressRequest(@NotNull(message = "不能为空") @Min(value = 0, message = "不能为负")
                                  Integer currentIndex) {
    }

    public record FinishResult(long durationSec, int answeredCount, int correctCount, Double correctRate,
                               List<Long> wrongQuestionIds) {
    }

    public record SessionListItem(Long id, String mode, int questionCount, int answeredCount, int correctCount,
                                  Instant startedAt, Instant completedAt) {
    }
}
