package com.richzds.quiz.controller;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.richzds.quiz.common.ApiResponse;
import com.richzds.quiz.common.PageQuery;
import com.richzds.quiz.common.PageResult;
import com.richzds.quiz.dto.WrongFavoriteDtos.FavoriteItem;
import com.richzds.quiz.dto.WrongFavoriteDtos.FavoriteRequest;
import com.richzds.quiz.security.CurrentUser;
import com.richzds.quiz.service.FavoriteService;

@Tag(name = "收藏")
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping
    public ApiResponse<PageResult<FavoriteItem>> list(@AuthenticationPrincipal CurrentUser user,
                                                      @RequestParam(required = false) Long categoryId,
                                                      PageQuery page) {
        return ApiResponse.ok(favoriteService.list(user.id(), categoryId, page));
    }

    @Operation(summary = "收藏或修改笔记；note 不传则不改笔记，传空串清空")
    @PutMapping("/{questionId}")
    public ApiResponse<FavoriteItem> upsert(@AuthenticationPrincipal CurrentUser user, @PathVariable long questionId,
                                            @Valid @RequestBody(required = false) FavoriteRequest req) {
        return ApiResponse.ok(favoriteService.upsert(user.id(), questionId, req == null ? null : req.note()));
    }

    @Operation(summary = "取消收藏，笔记一起删除")
    @DeleteMapping("/{questionId}")
    public ApiResponse<Void> remove(@AuthenticationPrincipal CurrentUser user, @PathVariable long questionId) {
        favoriteService.remove(user.id(), questionId);
        return ApiResponse.ok(null);
    }
}
