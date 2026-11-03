package com.richzds.quiz.config;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.richzds.quiz.mock.MockClock;

/**
 * 业务代码只从这个 Clock 取时间，测试里可以换成可调的时钟。
 * 演示数据模拟器（{@code app.mock.enabled=true}）运行时换成 {@link MockClock}，由模拟器按模拟时间推进。
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.mock.enabled:false}") boolean mock) {
        return mock ? new MockClock(Instant.now()) : Clock.systemUTC();
    }
}
