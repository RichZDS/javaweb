package com.richzds.quiz;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// 鉴权全部走 JWT，不需要 Boot 默认生成的内存用户
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class QuizApplication {

    public static void main(String[] args) {
        // 所有时间按 UTC 存取；“哪一天”另按 Asia/Shanghai 计算（见 TimeUtil）
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(QuizApplication.class, args);
    }
}
