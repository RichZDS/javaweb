package com.richzds.quiz.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 业务配置 {@code app.*}。缺少必填项时启动失败，错误信息里写明对应的环境变量。
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Admin admin,
        @Valid @NotNull Seed seed,
        @Valid @NotNull Upload upload,
        @Valid @NotNull Release release,
        @Valid @NotNull Cors cors) {

    public record Jwt(
            @NotBlank(message = "缺少 JWT 密钥：请设置环境变量 JWT_SECRET（app.jwt.secret，至少 32 字节）") String secret,
            @DefaultValue("7") int expireDays) {

        @AssertTrue(message = "JWT 密钥太短：JWT_SECRET（app.jwt.secret）至少 32 字节")
        public boolean isSecretLongEnough() {
            return secret == null || secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length >= 32;
        }
    }

    public record Admin(
            @NotBlank(message = "缺少管理员用户名：请设置环境变量 ADMIN_USERNAME（app.admin.username）") String username,
            @NotBlank(message = "缺少管理员密码：请设置环境变量 ADMIN_PASSWORD（app.admin.password）") String password) {
    }

    /** location 是种子文件所在目录（classpath: 或 file: 开头），读取其中全部 *.json。 */
    public record Seed(@DefaultValue("false") boolean enabled,
                       @DefaultValue("classpath:seed/") String location) {
    }

    public record Upload(@DefaultValue("./uploads") String dir) {
    }

    public record Release(@Valid @NotNull Platform android, @Valid @NotNull Platform ios) {
    }

    public record Platform(String latestVersion, String minVersion, String downloadUrl, String notes) {
    }

    /** 允许跨域的来源模式；为空时不开跨域（生产同域部署）。 */
    public record Cors(@DefaultValue List<String> allowedOriginPatterns) {
    }
}
