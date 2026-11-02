package com.richzds.quiz.security;

/** 当前登录用户，作为 Authentication 的 principal；控制器用 {@code @AuthenticationPrincipal} 取。 */
public record CurrentUser(Long id, String username, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
