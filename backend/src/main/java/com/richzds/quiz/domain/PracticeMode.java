package com.richzds.quiz.domain;

/** 练习模式（docs/api.md 第 4 节）。 */
public enum PracticeMode {
    RANDOM, SEQUENTIAL, WRONG, FAVORITE, DAILY, TAG, RETRY;

    /** 这种练习里答对时是否累加错题本的连对次数。 */
    public boolean countsWrongStreak() {
        return this == WRONG || this == RETRY;
    }
}
