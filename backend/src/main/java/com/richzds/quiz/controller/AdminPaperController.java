package com.richzds.quiz.controller;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.richzds.quiz.admin.AdminPaperService;
import com.richzds.quiz.common.ApiResponse;
import com.richzds.quiz.common.PageQuery;
import com.richzds.quiz.common.PageResult;
import com.richzds.quiz.dto.AdminDtos.AdminPaper;
import com.richzds.quiz.dto.AdminDtos.PaperInput;
import com.richzds.quiz.dto.AdminDtos.PaperStatusRequest;

@Tag(name = "后台-试卷")
@RestController
@RequestMapping("/api/admin/papers")
public class AdminPaperController {

    private final AdminPaperService service;

    public AdminPaperController(AdminPaperService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminPaper>> list(@RequestParam(required = false) Long categoryId,
                                                    @RequestParam(required = false) String status,
                                                    PageQuery page) {
        return ApiResponse.ok(service.list(categoryId, status, page));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminPaper> detail(@PathVariable long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @Operation(summary = "新建试卷（下架状态），总分自动算出")
    @PostMapping
    public ResponseEntity<ApiResponse<AdminPaper>> create(@Valid @RequestBody PaperInput in) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(in)));
    }

    @Operation(summary = "修改试卷；有人考过的只能改标题和描述")
    @PutMapping("/{id}")
    public ApiResponse<AdminPaper> update(@PathVariable long id, @Valid @RequestBody PaperInput in) {
        return ApiResponse.ok(service.update(id, in));
    }

    @Operation(summary = "上架 / 下架；上架时校验题量，规则卷不够 409 带 gaps")
    @PutMapping("/{id}/status")
    public ApiResponse<AdminPaper> status(@PathVariable long id, @Valid @RequestBody PaperStatusRequest req) {
        return ApiResponse.ok(service.changeStatus(id, req.status()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
