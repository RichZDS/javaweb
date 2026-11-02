package com.richzds.quiz.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.richzds.quiz.common.ApiResponse;
import com.richzds.quiz.dto.StatsDtos.CategoryItem;
import com.richzds.quiz.dto.StatsDtos.DailyItem;
import com.richzds.quiz.dto.StatsDtos.ExamItem;
import com.richzds.quiz.dto.StatsDtos.Leaderboard;
import com.richzds.quiz.dto.StatsDtos.Overview;
import com.richzds.quiz.dto.StatsDtos.WeakItem;
import com.richzds.quiz.security.CurrentUser;
import com.richzds.quiz.service.StatsService;

/** 统计接口都按本人；categoryId 不传时用目标考试。 */
@Tag(name = "统计")
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/overview")
    public ApiResponse<Overview> overview(@AuthenticationPrincipal CurrentUser user,
                                          @RequestParam(required = false) Long categoryId) {
        return ApiResponse.ok(statsService.overview(user.id(), categoryId));
    }

    @Operation(summary = "近 N 天（含今天，按北京时间）每天的作答，默认 30 天")
    @GetMapping("/daily")
    public ApiResponse<List<DailyItem>> daily(@AuthenticationPrincipal CurrentUser user,
                                              @RequestParam(required = false) Integer days,
                                              @RequestParam(required = false) Long categoryId) {
        return ApiResponse.ok(statsService.daily(user.id(), categoryId, days));
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategoryItem>> categories(@AuthenticationPrincipal CurrentUser user,
                                                      @RequestParam(required = false) Long categoryId) {
        return ApiResponse.ok(statsService.categories(user.id(), categoryId));
    }

    @Operation(summary = "薄弱点：近 N 天每题只看最后一次作答，至少 5 道不同题的章节按正确率升序")
    @GetMapping("/weak")
    public ApiResponse<List<WeakItem>> weak(@AuthenticationPrincipal CurrentUser user,
                                            @RequestParam(required = false) Integer days,
                                            @RequestParam(required = false) Integer limit,
                                            @RequestParam(required = false) Long categoryId) {
        return ApiResponse.ok(statsService.weak(user.id(), categoryId, days, limit));
    }

    @GetMapping("/exams")
    public ApiResponse<List<ExamItem>> exams(@AuthenticationPrincipal CurrentUser user,
                                             @RequestParam(required = false) Long paperId,
                                             @RequestParam(required = false) Long categoryId) {
        return ApiResponse.ok(statsService.exams(user.id(), categoryId, paperId));
    }

    @Operation(summary = "本周（北京时间周一 00:00 起）作答次数前 20")
    @GetMapping("/leaderboard")
    public ApiResponse<Leaderboard> leaderboard(@AuthenticationPrincipal CurrentUser user,
                                                @RequestParam(required = false) String period,
                                                @RequestParam(required = false) Long categoryId) {
        return ApiResponse.ok(statsService.leaderboard(user.id(), categoryId, period));
    }
}
