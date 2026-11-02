package com.richzds.quiz.common;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * 时间约定：库里的 DATETIME 一律是 UTC 的 {@link LocalDateTime}（不依赖 JVM 或驱动时区）；
 * 接口输出 {@link Instant}；“哪一天”按 {@link #BEIJING} 计算。
 */
public final class TimeUtil {

    public static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    private TimeUtil() {
    }

    /** 当前 UTC 时间，截到毫秒（和 DATETIME(3) 精度一致）。 */
    public static LocalDateTime nowUtc(Clock clock) {
        return LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC);
    }

    public static Instant toInstant(LocalDateTime utc) {
        return utc == null ? null : utc.toInstant(ZoneOffset.UTC);
    }

    public static LocalDateTime toUtc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** 北京时间的今天。 */
    public static LocalDate todayBeijing(Clock clock) {
        return LocalDate.ofInstant(clock.instant(), BEIJING);
    }

    /** 某个 UTC 时刻在北京时间是哪一天。 */
    public static LocalDate beijingDate(LocalDateTime utc) {
        return LocalDate.ofInstant(utc.toInstant(ZoneOffset.UTC), BEIJING);
    }

    /** 北京时间某天 00:00 对应的 UTC 时间。 */
    public static LocalDateTime beijingDayStartUtc(LocalDate day) {
        return LocalDateTime.ofInstant(day.atStartOfDay(BEIJING).toInstant(), ZoneOffset.UTC);
    }
}
