package com.richzds.quiz.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * prod 配置启动前检查必填的环境变量，缺几个就一次列出几个，免得改一个、启动、再报下一个。
 * 只在激活了 prod profile 时检查；dev / test 不受影响。
 */
public class ProdEnvironmentCheck implements EnvironmentPostProcessor {

    /** 必填的环境变量和说明。 */
    static final List<String[]> REQUIRED = List.of(
            new String[] {"DB_URL", "MySQL JDBC 地址"},
            new String[] {"DB_USERNAME", "数据库用户名"},
            new String[] {"DB_PASSWORD", "数据库密码"},
            new String[] {"JWT_SECRET", "JWT 密钥（至少 32 字节）"},
            new String[] {"ADMIN_PASSWORD", "管理员初始密码"});

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        if (!List.of(env.getActiveProfiles()).contains("prod")) {
            return;
        }
        List<String> problems = new ArrayList<>();
        for (String[] item : REQUIRED) {
            String value = env.getProperty(item[0]);
            if (value == null || value.isBlank()) {
                problems.add(item[0] + "（" + item[1] + "）未设置");
            }
        }
        String secret = env.getProperty("JWT_SECRET");
        if (secret != null && !secret.isBlank() && secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            problems.add("JWT_SECRET 只有 " + secret.getBytes(StandardCharsets.UTF_8).length + " 字节，至少要 32 字节");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("prod 配置缺少必要的环境变量：" + String.join("；", problems)
                    + "。请对照 deploy/.env.example 设置后重启。");
        }
    }
}
