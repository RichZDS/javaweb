package com.richzds.quiz.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 演示数据模拟器用的可调时钟：只在 {@code app.mock.enabled=true} 时作为业务的 Clock，由模拟器按事件推进。 */
public class MockClock extends Clock {

    private volatile Instant now;

    public MockClock(Instant start) {
        this.now = start;
    }

    public void set(Instant instant) {
        this.now = instant;
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("业务时间一律用 UTC");
    }
}
