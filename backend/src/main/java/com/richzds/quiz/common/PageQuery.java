package com.richzds.quiz.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/** 分页参数 {@code ?page=1&size=20}；page 从 1 开始，size 限制在 1–100。 */
public record PageQuery(Integer page, Integer size) {

    public static final int MAX_SIZE = 100;

    public long pageOrDefault() {
        return page == null || page < 1 ? 1 : page;
    }

    public long sizeOrDefault() {
        if (size == null || size < 1) {
            return 20;
        }
        return Math.min(size, MAX_SIZE);
    }

    public <T> Page<T> toPage() {
        return new Page<>(pageOrDefault(), sizeOrDefault());
    }
}
