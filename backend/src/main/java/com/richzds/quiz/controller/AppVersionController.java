package com.richzds.quiz.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.richzds.quiz.common.ApiResponse;
import com.richzds.quiz.common.BizException;
import com.richzds.quiz.config.AppProperties;
import com.richzds.quiz.config.AppProperties.Platform;

@Tag(name = "App 版本")
@RestController
public class AppVersionController {

    private final AppProperties props;

    public AppVersionController(AppProperties props) {
        this.props = props;
    }

    public record VersionInfo(String platform, String latestVersion, String minVersion, String downloadUrl,
                              String notes) {
    }

    @Operation(summary = "最新版本信息（不用登录），值来自配置 app.release.<platform>.*")
    @GetMapping("/api/app/version")
    public ApiResponse<VersionInfo> version(@RequestParam String platform) {
        Platform p = switch (platform) {
            case "android" -> props.release().android();
            case "ios" -> props.release().ios();
            default -> throw BizException.invalid("platform", "只能是 android 或 ios");
        };
        return ApiResponse.ok(new VersionInfo(platform, p.latestVersion(), p.minVersion(),
                emptyToNull(p.downloadUrl()), emptyToNull(p.notes())));
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
