package com.richzds.quiz.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.richzds.quiz.domain.FillOptions;
import com.richzds.quiz.dto.QuestionDtos.OptionView;

/** 管理后台（docs/api.md 第 9 节、9.1 节）。 */
public final class AdminDtos {

    private AdminDtos() {
    }

    // ------------------------------------------------------------------ 分类

    public record AdminCategory(Long id, Long parentId, String name, Integer level, Integer sort,
                                long questionCount, List<AdminCategory> children) {
    }

    public record CategoryCreate(Long parentId,
                                 @NotBlank(message = "不能为空") @Size(max = 50, message = "最多 50 字") String name,
                                 @Min(value = 0, message = "不能为负") @Max(value = 100000, message = "太大") Integer sort) {
    }

    public record CategoryUpdate(@NotBlank(message = "不能为空") @Size(max = 50, message = "最多 50 字") String name,
                                 @Min(value = 0, message = "不能为负") @Max(value = 100000, message = "太大") Integer sort) {
    }

    // ------------------------------------------------------------------ 题目

    public record OptionInput(@Size(max = 1, message = "选项 key 是一个大写字母") String key,
                              @Size(max = 5000, message = "选项太长") String text) {
    }

    public record QuestionInput(
            @NotNull(message = "不能为空") Long categoryId,
            @NotNull(message = "不能为空")
            @Pattern(regexp = "SINGLE|MULTIPLE|JUDGE|FILL", message = "题型不正确") String type,
            @NotBlank(message = "不能为空") @Size(max = 10000, message = "题干太长") String stem,
            @Valid @Size(max = 6, message = "最多 6 个选项") List<OptionInput> options,
            @Size(max = 6, message = "答案太多") List<@NotBlank(message = "不能为空") String> correctAnswer,
            @Size(max = 20, message = "最多 20 个空") List<@NotNull(message = "不能为空")
            @Size(max = 20, message = "可接受答案太多") List<@Size(max = 500, message = "答案太长") String>> acceptedAnswers,
            FillOptions fillOptions,
            @Size(max = 10000, message = "解析太长") String analysis,
            @Min(value = 1, message = "难度为 1–5") @Max(value = 5, message = "难度为 1–5") Integer difficulty,
            @Size(max = 10, message = "最多 10 个标签") List<@NotBlank(message = "不能为空")
            @Size(max = 30, message = "标签最多 30 字") String> tags,
            @Size(max = 255, message = "太长") String imageUrl,
            @Size(max = 255, message = "太长") String source,
            @Min(value = 0, message = "不能为负") @Max(value = 1000000, message = "太大") Integer sort,
            @Pattern(regexp = "DRAFT|PUBLISHED", message = "只能是 DRAFT 或 PUBLISHED") String status) {
    }

    public record AdminQuestion(Long id, Long categoryId, String categoryPath, String type, String stem,
                                String stemHtml, List<OptionView> options, List<String> correctAnswer,
                                List<List<String>> acceptedAnswers, FillOptions fillOptions, String analysis,
                                String analysisHtml, Integer difficulty, List<String> tags, String imageUrl,
                                String source, Integer sort, String status, int blankCount, Integer version,
                                long answerCount, Double correctRate, Instant createdAt, Instant updatedAt) {
    }

    /** 列表项：同 AdminQuestion，但不含 stemHtml、analysisHtml，另加 stemPreview。 */
    public record AdminQuestionItem(Long id, Long categoryId, String categoryPath, String type, String stem,
                                    String stemPreview, List<OptionView> options, List<String> correctAnswer,
                                    List<List<String>> acceptedAnswers, FillOptions fillOptions, String analysis,
                                    Integer difficulty, List<String> tags, String imageUrl, String source,
                                    Integer sort, String status, int blankCount, Integer version, long answerCount,
                                    Double correctRate, Instant createdAt, Instant updatedAt) {
    }

    public record DeleteResult(boolean deleted, boolean archived) {
    }

    public record PreviewRequest(@NotNull(message = "不能为空") @Size(max = 20000, message = "太长") String markdown) {
    }

    public record PreviewResult(String html) {
    }

    public record ImportResult(int total, int success, List<ImportFailure> failed) {
    }

    public record ImportFailure(int row, String reason) {
    }

    // ------------------------------------------------------------------ 试卷

    public record PaperQuestionInput(@NotNull(message = "不能为空") Long questionId,
                                     @NotNull(message = "不能为空") @Min(value = 1, message = "分值至少 1")
                                     @Max(value = 100, message = "分值最多 100") Integer score,
                                     @Min(value = 0, message = "不能为负") Integer sort) {
    }

    public record PaperRuleInput(@NotNull(message = "不能为空") Long categoryId,
                                 @NotNull(message = "不能为空")
                                 @Pattern(regexp = "SINGLE|MULTIPLE|JUDGE|FILL", message = "题型不正确") String type,
                                 @NotNull(message = "不能为空") @Min(value = 1, message = "至少 1 道")
                                 @Max(value = 200, message = "最多 200 道") Integer count,
                                 @NotNull(message = "不能为空") @Min(value = 1, message = "分值至少 1")
                                 @Max(value = 100, message = "分值最多 100") Integer score) {
    }

    public record PaperInput(
            @NotBlank(message = "不能为空") @Size(max = 100, message = "最多 100 字") String title,
            @NotNull(message = "不能为空") Long categoryId,
            @Size(max = 5000, message = "太长") String description,
            @NotNull(message = "不能为空") @Pattern(regexp = "FIXED|RULE", message = "只能是 FIXED 或 RULE") String mode,
            @NotNull(message = "不能为空") @Min(value = 1, message = "至少 1 分钟")
            @Max(value = 600, message = "最多 600 分钟") Integer durationMin,
            @NotNull(message = "不能为空") @Min(value = 0, message = "不能为负") Integer passScore,
            @Valid @Size(max = 200, message = "最多 200 道题") List<PaperQuestionInput> questions,
            @Valid @Size(max = 50, message = "最多 50 条规则") List<PaperRuleInput> rules,
            Boolean shuffleOptions) {
    }

    public record PaperStatusRequest(@NotNull(message = "不能为空")
                                     @Pattern(regexp = "ONLINE|OFFLINE", message = "只能是 ONLINE 或 OFFLINE")
                                     String status) {
    }

    public record AdminPaper(Long id, String title, Long categoryId, String categoryName, String description,
                             String mode, Integer durationMin, Integer totalScore, Integer passScore, String status,
                             boolean shuffleOptions, int questionCount, long attempts, List<AdminPaperQuestion> questions,
                             List<AdminPaperRule> rules, Instant createdAt, Instant updatedAt) {
    }

    public record AdminPaperQuestion(Long questionId, Integer score, Integer sort, String type, String stemPreview) {
    }

    public record AdminPaperRule(Long categoryId, String categoryPath, String type, Integer count, Integer score) {
    }

    // ------------------------------------------------------------------ 用户、纠错、看板

    public record AdminUser(Long id, String username, String nickname, String role, String status, long answerCount,
                            Instant lastActiveAt, Instant createdAt) {
    }

    public record UserStatusRequest(@NotNull(message = "不能为空")
                                    @Pattern(regexp = "ACTIVE|DISABLED", message = "只能是 ACTIVE 或 DISABLED")
                                    String status) {
    }

    /** 纠错列表项；type 是纠错类型（ANSWER_WRONG 等），questionType 是题目的题型。 */
    public record FeedbackItem(Long id, Long questionId, String stemPreview, String categoryPath, String type,
                               String questionType, String content, String status, String reply, String username,
                               Instant createdAt, Instant handledAt) {
    }

    public record FeedbackHandle(@NotNull(message = "不能为空")
                                 @Pattern(regexp = "RESOLVED|REJECTED", message = "只能是 RESOLVED 或 REJECTED")
                                 String status,
                                 @Size(max = 500, message = "最多 500 字") String reply) {
    }

    public record Dashboard(long userCount, long questionCount, long paperCount, long todayAnswerCount,
                            long pendingFeedbackCount, List<DashboardDay> daily, List<DashboardPaper> papers) {
    }

    public record DashboardDay(LocalDate date, long answerCount, long activeUsers) {
    }

    public record DashboardPaper(Long paperId, String title, long attempts, Double avgScore, Double passRate) {
    }
}
