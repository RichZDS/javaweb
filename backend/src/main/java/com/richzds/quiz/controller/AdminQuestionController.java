package com.richzds.quiz.controller;

import java.nio.charset.StandardCharsets;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import com.richzds.quiz.admin.AdminQuestionService;
import com.richzds.quiz.admin.QuestionImportService;
import com.richzds.quiz.common.ApiResponse;
import com.richzds.quiz.common.PageQuery;
import com.richzds.quiz.common.PageResult;
import com.richzds.quiz.dto.AdminDtos.AdminQuestion;
import com.richzds.quiz.dto.AdminDtos.AdminQuestionItem;
import com.richzds.quiz.dto.AdminDtos.DeleteResult;
import com.richzds.quiz.dto.AdminDtos.ImportResult;
import com.richzds.quiz.dto.AdminDtos.PreviewRequest;
import com.richzds.quiz.dto.AdminDtos.PreviewResult;
import com.richzds.quiz.dto.AdminDtos.QuestionInput;
import com.richzds.quiz.service.QuestionAssembler;

@Tag(name = "后台-题目")
@RestController
@RequestMapping("/api/admin/questions")
public class AdminQuestionController {

    static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AdminQuestionService service;
    private final QuestionImportService importService;
    private final QuestionAssembler assembler;

    public AdminQuestionController(AdminQuestionService service, QuestionImportService importService,
                                   QuestionAssembler assembler) {
        this.service = service;
        this.importService = importService;
        this.assembler = assembler;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminQuestionItem>> list(@RequestParam(required = false) Long categoryId,
                                                           @RequestParam(required = false) String type,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(required = false) String keyword,
                                                           PageQuery page) {
        return ApiResponse.ok(service.list(categoryId, type, status, keyword, page));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminQuestion> detail(@PathVariable long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @Operation(summary = "新建题目；status=PUBLISHED 时必须有正确答案和解析")
    @PostMapping
    public ResponseEntity<ApiResponse<AdminQuestion>> create(@Valid @RequestBody QuestionInput in) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(in)));
    }

    @Operation(summary = "修改题目，version + 1")
    @PutMapping("/{id}")
    public ApiResponse<AdminQuestion> update(@PathVariable long id, @Valid @RequestBody QuestionInput in) {
        return ApiResponse.ok(service.update(id, in));
    }

    @Operation(summary = "删除：没人用过就真删，否则改成归档")
    @DeleteMapping("/{id}")
    public ApiResponse<DeleteResult> delete(@PathVariable long id) {
        return ApiResponse.ok(service.delete(id));
    }

    @Operation(summary = "Markdown 预览")
    @PostMapping("/preview")
    public ApiResponse<PreviewResult> preview(@Valid @RequestBody PreviewRequest req) {
        return ApiResponse.ok(new PreviewResult(assembler.html(req.markdown())));
    }

    @Operation(summary = "下载 Excel 导入模板")
    @GetMapping("/import/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("题目导入模板.xlsx", StandardCharsets.UTF_8).build().toString())
                .body(importService.template());
    }

    @Operation(summary = "Excel 导入（xlsx，≤ 5 MB）：整表先校验，只导入正确的行，错误行逐行报原因")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportResult> importQuestions(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(importService.importFile(file));
    }
}
