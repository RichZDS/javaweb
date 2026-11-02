package com.richzds.quiz.controller;

import java.util.List;

import jakarta.validation.Valid;

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
import org.springframework.web.bind.annotation.RestController;

import com.richzds.quiz.admin.AdminCategoryService;
import com.richzds.quiz.common.ApiResponse;
import com.richzds.quiz.dto.AdminDtos.AdminCategory;
import com.richzds.quiz.dto.AdminDtos.CategoryCreate;
import com.richzds.quiz.dto.AdminDtos.CategoryUpdate;

@Tag(name = "后台-分类")
@RestController
@RequestMapping("/api/admin/categories")
public class AdminCategoryController {

    private final AdminCategoryService service;

    public AdminCategoryController(AdminCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<AdminCategory>> tree() {
        return ApiResponse.ok(service.tree());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminCategory>> create(@Valid @RequestBody CategoryCreate req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(req)));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminCategory> update(@PathVariable long id, @Valid @RequestBody CategoryUpdate req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
