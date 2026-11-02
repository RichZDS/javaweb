package com.richzds.quiz.controller;

import java.util.List;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.richzds.quiz.common.ApiResponse;
import com.richzds.quiz.common.PageQuery;
import com.richzds.quiz.common.PageResult;
import com.richzds.quiz.dto.ExamDtos.ExamResult;
import com.richzds.quiz.dto.ExamDtos.ExamSheet;
import com.richzds.quiz.dto.ExamDtos.PaperDetail;
import com.richzds.quiz.dto.ExamDtos.PaperItem;
import com.richzds.quiz.dto.ExamDtos.RecordItem;
import com.richzds.quiz.dto.ExamDtos.SaveAnswerRequest;
import com.richzds.quiz.dto.ExamDtos.SaveAnswerResult;
import com.richzds.quiz.exam.ExamService;
import com.richzds.quiz.exam.ExamService.Started;
import com.richzds.quiz.security.CurrentUser;

@Tag(name = "模拟考试")
@RestController
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    @Operation(summary = "已上架试卷，默认按本人目标考试过滤")
    @GetMapping("/api/exams")
    public ApiResponse<List<PaperItem>> papers(@AuthenticationPrincipal CurrentUser user,
                                               @RequestParam(required = false) Long categoryId) {
        return ApiResponse.ok(examService.papers(user.id(), categoryId));
    }

    @Operation(summary = "考前须知")
    @GetMapping("/api/exams/{paperId}")
    public ApiResponse<PaperDetail> paper(@AuthenticationPrincipal CurrentUser user, @PathVariable long paperId) {
        return ApiResponse.ok(examService.paper(user.id(), paperId));
    }

    @Operation(summary = "开考：已有进行中的同卷考试返回它（200），否则新建（201）；规则卷题不够 409 带 gaps")
    @PostMapping("/api/exams/{paperId}/start")
    public ResponseEntity<ApiResponse<ExamSheet>> start(@AuthenticationPrincipal CurrentUser user,
                                                        @PathVariable long paperId) {
        Started started = examService.start(user.id(), paperId);
        return ResponseEntity.status(started.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.ok(started.sheet()));
    }

    @Operation(summary = "进行中返回 ExamSheet，已结算返回 ExamResult")
    @GetMapping("/api/exam-records/{id}")
    public ApiResponse<Object> record(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
        return ApiResponse.ok(examService.record(user.id(), id));
    }

    @Operation(summary = "保存一题答案：只接受更大的 version；截止后 409 EXAM_EXPIRED")
    @PutMapping("/api/exam-records/{id}/answers/{questionId}")
    public ApiResponse<SaveAnswerResult> saveAnswer(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
                                                    @PathVariable long questionId,
                                                    @Valid @RequestBody SaveAnswerRequest req) {
        return ApiResponse.ok(examService.saveAnswer(user.id(), id, questionId, req));
    }

    @Operation(summary = "交卷并结算，可重复调用，只结算一次")
    @PostMapping("/api/exam-records/{id}/submit")
    public ApiResponse<ExamResult> submit(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
        return ApiResponse.ok(examService.submit(user.id(), id));
    }

    @Operation(summary = "考试记录")
    @GetMapping("/api/exam-records")
    public ApiResponse<PageResult<RecordItem>> records(@AuthenticationPrincipal CurrentUser user,
                                                       @RequestParam(required = false) Long paperId,
                                                       PageQuery page) {
        return ApiResponse.ok(examService.records(user.id(), paperId, page));
    }
}
