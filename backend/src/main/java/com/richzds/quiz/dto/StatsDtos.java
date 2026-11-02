package com.richzds.quiz.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 统计（docs/api.md 第 7 节）。 */
public final class StatsDtos {

    private StatsDtos() {
    }

    public record Overview(long answerCount, long correctCount, Double correctRate, long studyDays, int streakDays,
                           long todayAnswerCount, long examCount) {
    }

    public record DailyItem(LocalDate date, long answerCount, long correctCount, Double correctRate) {
    }

    public record CategoryItem(Long categoryId, String categoryPath, long questionCount, long doneCount,
                               long answerCount, Double correctRate) {
    }

    public record WeakItem(Long categoryId, String categoryPath, int sampleCount, Double correctRate) {
    }

    public record ExamItem(Long recordId, Long paperId, String title, int score, int totalScore, int passScore,
                           Double rate, Instant submittedAt) {
    }

    public record Leaderboard(List<LeaderItem> items, MyRank me) {
    }

    public record LeaderItem(int rank, String nickname, String avatarUrl, long answerCount) {
    }

    public record MyRank(Integer rank, long answerCount) {
    }
}
