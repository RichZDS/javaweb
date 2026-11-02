package com.richzds.quiz.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.richzds.quiz.common.ApiResponse;
import com.richzds.quiz.common.PageQuery;
import com.richzds.quiz.common.PageResult;
import com.richzds.quiz.dto.WrongFavoriteDtos.WrongGroup;
import com.richzds.quiz.dto.WrongFavoriteDtos.WrongItem;
import com.richzds.quiz.security.CurrentUser;
import com.richzds.quiz.service.WrongBookService;

@Tag(name = "错题本")
@RestController
@RequestMapping("/api/wrong-questions")
public class WrongQuestionController {

    private final WrongBookService wrongBookService;

    public WrongQuestionController(WrongBookService wrongBookService) {
        this.wrongBookService = wrongBookService;
    }

    @Operation(summary = "错题列表，sort=RECENT（默认）或 MOST")
    @GetMapping
    public ApiResponse<PageResult<WrongItem>> list(@AuthenticationPrincipal CurrentUser user,
                                                   @RequestParam(required = false) Long categoryId,
                                                   @RequestParam(required = false, defaultValue = "RECENT") String sort,
                                                   PageQuery page) {
        return ApiResponse.ok(wrongBookService.list(user.id(), categoryId, sort, page));
    }

    @Operation(summary = "按章节分组计数")
    @GetMapping("/groups")
    public ApiResponse<List<WrongGroup>> groups(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(wrongBookService.groups(user.id()));
    }

    @Operation(summary = "手动移出错题本")
    @DeleteMapping("/{questionId}")
    public ApiResponse<Void> remove(@AuthenticationPrincipal CurrentUser user, @PathVariable long questionId) {
        wrongBookService.remove(user.id(), questionId);
        return ApiResponse.ok(null);
    }
}
