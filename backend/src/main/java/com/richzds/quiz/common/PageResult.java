package com.richzds.quiz.common;

import java.util.List;
import java.util.function.Function;

import com.baomidou.mybatisplus.core.metadata.IPage;

/** 分页返回 {@code {items, total, page, size}}。 */
public record PageResult<T>(List<T> items, long total, long page, long size) {

    public static <E, T> PageResult<T> of(IPage<E> page, Function<E, T> mapper) {
        return new PageResult<>(page.getRecords().stream().map(mapper).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    public static <T> PageResult<T> of(IPage<?> page, List<T> items) {
        return new PageResult<>(items, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
