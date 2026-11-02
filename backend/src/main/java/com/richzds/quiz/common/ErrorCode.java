package com.richzds.quiz.common;

import org.springframework.http.HttpStatus;

/** 错误码，与 docs/api.md 第 0 节一致。 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "参数不合法"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "未登录或登录已过期"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "没有权限"),
    EXAM_IN_PROGRESS(HttpStatus.FORBIDDEN, "考试进行中，结束前不能查看这道题"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在"),
    USERNAME_TAKEN(HttpStatus.CONFLICT, "用户名已存在"),
    POOL_INSUFFICIENT(HttpStatus.CONFLICT, "题目数量不够"),
    EXAM_EXPIRED(HttpStatus.CONFLICT, "考试已过截止时间"),
    CONFLICT(HttpStatus.CONFLICT, "状态冲突"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务器错误");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
