package com.richzds.quiz.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Size;

/** 错题本和收藏（docs/api.md 第 5 节）。 */
public final class WrongFavoriteDtos {

    private WrongFavoriteDtos() {
    }

    public record WrongItem(Long questionId, Long categoryId, String categoryPath, String type, String stemPreview,
                            int wrongCount, int correctStreak, List<String> lastWrongAnswer, Instant lastWrongAt) {
    }

    public record WrongGroup(Long categoryId, String categoryPath, long count) {
    }

    public record FavoriteItem(Long questionId, String categoryPath, String type, String stemPreview, String note,
                               Instant createdAt) {
    }

    public record FavoriteRequest(@Size(max = 200, message = "笔记最多 200 字") String note) {
    }
}
