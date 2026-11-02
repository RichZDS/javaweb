package com.richzds.quiz.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.richzds.quiz.dto.QuestionDtos.OptionView;

/** 模拟考试（docs/api.md 第 6 节）。 */
public final class ExamDtos {

    private ExamDtos() {
    }

    public record PaperItem(Long paperId, String title, Long categoryId, String mode, int questionCount,
                            int totalScore, int passScore, int durationMin, int myAttempts, Integer myBestScore,
                            Long inProgressRecordId) {
    }

    public record PaperDetail(Long paperId, String title, Long categoryId, String mode, int questionCount,
                              int totalScore, int passScore, int durationMin, int myAttempts, Integer myBestScore,
                              Long inProgressRecordId, String description, String scoringRules) {
    }

    /** 进行中的答卷，不含任何答案和解析。 */
    public record ExamSheet(Long recordId, Long paperId, String title, String status, Instant startedAt,
                            Instant deadlineAt, Instant serverTime, int durationMin, int totalScore, int passScore,
                            List<SheetQuestion> questions, List<SavedAnswer> answers) {
    }

    public record SheetQuestion(int index, Long questionId, String type, String stemHtml, List<OptionView> options,
                                int blankCount, String imageUrl, int score) {
    }

    public record SavedAnswer(Long questionId, List<String> userAnswer, int version, boolean marked,
                              Instant savedAt) {
    }

    public record SaveAnswerRequest(
            @NotNull(message = "不能为空") @Size(max = 20, message = "答案项太多")
            List<@Size(max = 500, message = "答案太长") String> userAnswer,
            @NotNull(message = "不能为空") @Min(value = 1, message = "版本号从 1 开始")
            @Max(value = Integer.MAX_VALUE, message = "太大") Integer version,
            Boolean marked) {
    }

    public record SaveAnswerResult(boolean accepted, int version) {
    }

    public record ExamResult(Long recordId, Long paperId, String title, String status, int score, int totalScore,
                             int passScore, boolean passed, Instant startedAt, Instant submittedAt, long durationSec,
                             int correctCount, int wrongCount, int unansweredCount, List<TypeScore> byType,
                             List<CategoryScore> byCategory, List<ResultItem> items) {
    }

    public record TypeScore(String type, int score, int total, Double rate) {
    }

    public record CategoryScore(Long categoryId, String categoryPath, int score, int total, Double rate) {
    }

    public record ResultItem(int index, Long questionId, String type, String stemHtml, List<OptionView> options,
                             List<String> userAnswer, List<String> correctAnswer, List<List<String>> acceptedAnswers,
                             boolean correct, int score, int maxScore, String analysisHtml) {
    }

    public record RecordItem(Long recordId, Long paperId, String title, String status, Integer score,
                             int totalScore, Boolean passed, Instant startedAt, Instant submittedAt) {
    }

    /** 规则卷题量缺口。 */
    public record Gap(Long categoryId, String categoryPath, String type, int need, int available) {
    }
}
