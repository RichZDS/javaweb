package com.richzds.quiz.controller;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.richzds.quiz.common.ApiResponse;
import com.richzds.quiz.common.PageQuery;
import com.richzds.quiz.common.PageResult;
import com.richzds.quiz.dto.QuestionDtos.FeedbackRequest;
import com.richzds.quiz.dto.QuestionDtos.FeedbackView;
import com.richzds.quiz.dto.QuestionDtos.QuestionDetail;
import com.richzds.quiz.dto.QuestionDtos.SearchItem;
import com.richzds.quiz.security.CurrentUser;
import com.richzds.quiz.service.QuestionService;

@Tag(name = "题目")
@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @Operation(summary = "按题干关键词搜题；keyword 必填，去掉首尾空白后 1–50 字")
    @GetMapping("/search")
    public ApiResponse<PageResult<SearchItem>> search(@RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) Long categoryId,
                                                      PageQuery page) {
        return ApiResponse.ok(questionService.search(keyword, categoryId, page));
    }

    @Operation(summary = "题目详情（含答案和解析）；进行中的考试里的题返回 403 EXAM_IN_PROGRESS")
    @GetMapping("/{id}")
    public ApiResponse<QuestionDetail> detail(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
        return ApiResponse.ok(questionService.detail(user.id(), id));
    }

    @Operation(summary = "题目纠错")
    @PostMapping("/{id}/feedback")
    public ResponseEntity<ApiResponse<FeedbackView>> feedback(@AuthenticationPrincipal CurrentUser user,
                                                              @PathVariable long id,
                                                              @Valid @RequestBody FeedbackRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(questionService.feedback(user.id(), id, req)));
    }
}
