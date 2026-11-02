package com.richzds.quiz.dto;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 账号相关的请求和返回（docs/api.md 第 2 节）。 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "不能为空")
            @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$", message = "用户名为 3–20 位字母、数字或下划线")
            String username,
            @NotBlank(message = "不能为空") @Size(min = 6, max = 32, message = "密码为 6–32 位") String password,
            @Size(max = 30, message = "昵称最多 30 字") String nickname) {
    }

    public record LoginRequest(@NotBlank(message = "不能为空") String username,
                               @NotBlank(message = "不能为空") String password) {
    }

    public record AuthResult(String token, Instant expiresAt, UserView user) {
    }

    public record UserView(Long id, String username, String nickname, String avatarUrl, String role,
                           String status, Long targetCategoryId, String targetCategoryName,
                           int wrongRemoveThreshold, Instant createdAt) {
    }

    public record UpdateMeRequest(
            @Size(min = 1, max = 30, message = "昵称为 1–30 字") String nickname,
            @Size(max = 255, message = "头像地址太长") String avatarUrl,
            Long targetCategoryId) {
    }

    public record SettingsRequest(
            @NotNull(message = "不能为空") @Min(value = 1, message = "只能是 1–10") @Max(value = 10, message = "只能是 1–10")
            Integer wrongRemoveThreshold) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "不能为空") String oldPassword,
            @NotBlank(message = "不能为空") @Size(min = 6, max = 32, message = "密码为 6–32 位") String newPassword) {
    }
}
