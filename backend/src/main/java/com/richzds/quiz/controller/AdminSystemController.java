package com.richzds.quiz.controller;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.richzds.quiz.admin.AdminSystemService;
import com.richzds.quiz.common.ApiResponse;
import com.richzds.quiz.common.PageQuery;
import com.richzds.quiz.common.PageResult;
import com.richzds.quiz.dto.AdminDtos.AdminUser;
import com.richzds.quiz.dto.AdminDtos.Dashboard;
import com.richzds.quiz.dto.AdminDtos.FeedbackHandle;
import com.richzds.quiz.dto.AdminDtos.FeedbackItem;
import com.richzds.quiz.dto.AdminDtos.UserStatusRequest;
import com.richzds.quiz.security.CurrentUser;

@Tag(name = "后台-用户、纠错、看板")
@RestController
@RequestMapping("/api/admin")
public class AdminSystemController {

    private final AdminSystemService service;

    public AdminSystemController(AdminSystemService service) {
        this.service = service;
    }

    @GetMapping("/users")
    public ApiResponse<PageResult<AdminUser>> users(@RequestParam(required = false) String keyword, PageQuery page) {
        return ApiResponse.ok(service.users(keyword, page));
    }

    @Operation(summary = "启用 / 禁用用户，不能禁用自己")
    @PutMapping("/users/{id}/status")
    public ApiResponse<AdminUser> userStatus(@AuthenticationPrincipal CurrentUser admin, @PathVariable long id,
                                             @Valid @RequestBody UserStatusRequest req) {
        return ApiResponse.ok(service.changeUserStatus(admin.id(), id, req.status()));
    }

    @GetMapping("/feedback")
    public ApiResponse<PageResult<FeedbackItem>> feedback(@RequestParam(required = false) String status,
                                                          PageQuery page) {
        return ApiResponse.ok(service.feedback(status, page));
    }

    @PutMapping("/feedback/{id}")
    public ApiResponse<FeedbackItem> handle(@PathVariable long id, @Valid @RequestBody FeedbackHandle req) {
        return ApiResponse.ok(service.handleFeedback(id, req));
    }

    @Operation(summary = "数据看板，daily 为近 14 天")
    @GetMapping("/dashboard")
    public ApiResponse<Dashboard> dashboard() {
        return ApiResponse.ok(service.dashboard());
    }
}
