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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.richzds.quiz.common.ApiResponse;
import com.richzds.quiz.common.PageQuery;
import com.richzds.quiz.common.PageResult;
import com.richzds.quiz.dto.PracticeDtos.FinishResult;
import com.richzds.quiz.dto.PracticeDtos.ProgressRequest;
import com.richzds.quiz.dto.PracticeDtos.SessionListItem;
import com.richzds.quiz.dto.PracticeDtos.SessionView;
import com.richzds.quiz.dto.PracticeDtos.StartRequest;
import com.richzds.quiz.dto.PracticeDtos.SubmitRequest;
import com.richzds.quiz.dto.PracticeDtos.SubmitResult;
import com.richzds.quiz.security.CurrentUser;
import com.richzds.quiz.service.PracticeService;
import com.richzds.quiz.service.PracticeService.Started;

@Tag(name = "练习")
@RestController
@RequestMapping("/api/practice/sessions")
public class PracticeController {

    private final PracticeService practiceService;

    public PracticeController(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    @Operation(summary = "开始一次练习：新建返回 201，复用已有的（顺序练习、每日一练）返回 200")
    @PostMapping
    public ResponseEntity<ApiResponse<SessionView>> start(@AuthenticationPrincipal CurrentUser user,
                                                          @Valid @RequestBody StartRequest req) {
        Started started = practiceService.start(user.id(), req);
        return ResponseEntity.status(started.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.ok(started.session()));
    }

    @Operation(summary = "目标考试（或 categoryId 指定的一级分类）下最近一次未完成的非每日练习，没有时 data 为 null")
    @GetMapping("/current")
    public ApiResponse<SessionView> current(@AuthenticationPrincipal CurrentUser user,
                                            @RequestParam(required = false) Long categoryId) {
        return ApiResponse.ok(practiceService.current(user.id(), categoryId));
    }

    @Operation(summary = "练习记录")
    @GetMapping
    public ApiResponse<PageResult<SessionListItem>> list(@AuthenticationPrincipal CurrentUser user, PageQuery page) {
        return ApiResponse.ok(practiceService.list(user.id(), page));
    }

    @GetMapping("/{id}")
    public ApiResponse<SessionView> get(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
        return ApiResponse.ok(practiceService.get(user.id(), id));
    }

    @Operation(summary = "提交一题；重复提交返回第一次的结果且 alreadySubmitted=true")
    @PostMapping("/{id}/answers")
    public ApiResponse<SubmitResult> submit(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
                                            @Valid @RequestBody SubmitRequest req) {
        return ApiResponse.ok(practiceService.submit(user.id(), id, req));
    }

    @Operation(summary = "保存看到第几题")
    @PutMapping("/{id}/progress")
    public ApiResponse<Void> progress(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
                                      @Valid @RequestBody ProgressRequest req) {
        practiceService.saveProgress(user.id(), id, req.currentIndex());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/finish")
    public ApiResponse<FinishResult> finish(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
        return ApiResponse.ok(practiceService.finish(user.id(), id));
    }
}
