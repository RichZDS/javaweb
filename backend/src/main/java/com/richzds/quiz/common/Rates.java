package com.richzds.quiz.common;

/** 比率统一保留 4 位小数；分母为 0 时为 null。 */
public final class Rates {

    private Rates() {
    }

    public static Double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return null;
        }
        return Math.round(numerator * 10000.0 / denominator) / 10000.0;
    }
}
