package com.richzds.quiz.controller;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.richzds.quiz.common.ApiResponse;
import com.richzds.quiz.dto.AuthDtos.AuthResult;
import com.richzds.quiz.dto.AuthDtos.ChangePasswordRequest;
import com.richzds.quiz.dto.AuthDtos.LoginRequest;
import com.richzds.quiz.dto.AuthDtos.RegisterRequest;
import com.richzds.quiz.dto.AuthDtos.SettingsRequest;
import com.richzds.quiz.dto.AuthDtos.UpdateMeRequest;
import com.richzds.quiz.dto.AuthDtos.UserView;
import com.richzds.quiz.security.CurrentUser;
import com.richzds.quiz.service.UserService;

@Tag(name = "账号")
@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "注册（新建账号，返回 201）")
    @PostMapping("/auth/register")
    public ResponseEntity<ApiResponse<AuthResult>> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(userService.register(req)));
    }

    @Operation(summary = "登录")
    @PostMapping("/auth/login")
    public ApiResponse<AuthResult> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(userService.login(req));
    }

    @GetMapping("/me")
    public ApiResponse<UserView> me(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(userService.me(user.id()));
    }

    @Operation(summary = "改昵称、头像、目标考试")
    @PutMapping("/me")
    public ApiResponse<UserView> updateMe(@AuthenticationPrincipal CurrentUser user,
                                          @Valid @RequestBody UpdateMeRequest req) {
        return ApiResponse.ok(userService.updateMe(user.id(), req));
    }

    @Operation(summary = "个人设置：错题连续答对几次自动移出（1–10）")
    @PutMapping("/me/settings")
    public ApiResponse<UserView> updateSettings(@AuthenticationPrincipal CurrentUser user,
                                                @Valid @RequestBody SettingsRequest req) {
        return ApiResponse.ok(userService.updateSettings(user.id(), req.wrongRemoveThreshold()));
    }

    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal CurrentUser user,
                                            @Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(user.id(), req);
        return ApiResponse.ok(null);
    }
}
