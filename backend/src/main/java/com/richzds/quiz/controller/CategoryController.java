package com.richzds.quiz.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.richzds.quiz.common.ApiResponse;
import com.richzds.quiz.security.CurrentUser;
import com.richzds.quiz.service.CategoryProgressService;

@Tag(name = "题库分类")
@RestController
public class CategoryController {

    private final CategoryProgressService progressService;

    public CategoryController(CategoryProgressService progressService) {
        this.progressService = progressService;
    }

    /** 不传 rootId 时 data 是一级分类数组；传了时 data 是该考试的根节点对象（children 里是整棵树）。 */
    @Operation(summary = "分类树（带本人进度）：不传 rootId 返回一级分类数组，传了返回该考试的根节点")
    @GetMapping("/api/categories")
    public ApiResponse<Object> tree(@AuthenticationPrincipal CurrentUser user,
                                    @RequestParam(required = false) Long rootId) {
        if (rootId == null) {
            return ApiResponse.ok(progressService.roots(user.id()));
        }
        return ApiResponse.ok(progressService.tree(user.id(), rootId));
    }
}
