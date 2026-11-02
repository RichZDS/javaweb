package com.richzds.quiz.common;

/** 统一返回体 {@code {code, message, data}}。 */
public record ApiResponse<T>(String code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "ok", data);
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message, T data) {
        return new ApiResponse<>(code.name(), message, data);
    }
}
