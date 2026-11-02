package com.richzds.quiz.domain;

/** 题型。 */
public enum QuestionType {
    SINGLE, MULTIPLE, JUDGE, FILL;

    public boolean isChoice() {
        return this == SINGLE || this == MULTIPLE;
    }
}
