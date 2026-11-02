package com.richzds.quiz.domain;

/** 填空题判分开关。 */
public record FillOptions(boolean ignoreCase, boolean ignoreSpaces) {

    public static final FillOptions DEFAULT = new FillOptions(false, false);
}
