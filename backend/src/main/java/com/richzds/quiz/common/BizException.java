package com.richzds.quiz.common;

import java.util.List;
import java.util.Map;

/** 业务异常：携带错误码、提示和可选的 data 细节，由 GlobalExceptionHandler 转成统一返回。 */
public class BizException extends RuntimeException {

    private final ErrorCode code;
    private final transient Object data;

    public BizException(ErrorCode code, String message, Object data) {
        super(message == null ? code.defaultMessage() : message);
        this.code = code;
        this.data = data;
    }

    public BizException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public BizException(ErrorCode code) {
        this(code, null, null);
    }

    public ErrorCode code() {
        return code;
    }

    public Object data() {
        return data;
    }

    public static BizException notFound(String what) {
        return new BizException(ErrorCode.NOT_FOUND, what + "不存在");
    }

    public static BizException forbidden(String message) {
        return new BizException(ErrorCode.FORBIDDEN, message);
    }

    public static BizException conflict(String message) {
        return new BizException(ErrorCode.CONFLICT, message);
    }

    /** 单个字段校验失败，data 形如 {@code {fields: [{field, message}]}}。 */
    public static BizException invalid(String field, String message) {
        return new BizException(ErrorCode.VALIDATION_FAILED, message,
                Map.of("fields", List.of(new FieldError(field, message))));
    }

    public record FieldError(String field, String message) {
    }
}
