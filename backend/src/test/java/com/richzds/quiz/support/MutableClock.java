package com.richzds.quiz.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 测试用的可调时钟：业务代码注入的 Clock 在测试里换成它。 */
public class MutableClock extends Clock {

    public static final Instant DEFAULT = Instant.parse("2026-11-02T08:00:00Z");

    private volatile Instant now = DEFAULT;

    public void set(Instant instant) {
        this.now = instant;
    }

    public void advance(Duration d) {
        this.now = now.plus(d);
    }

    public void reset() {
        this.now = DEFAULT;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
