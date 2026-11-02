package com.richzds.quiz.common;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.richzds.quiz.common.BizException.FieldError;

/** 把各类异常统一成 {@code {code, message, data}}，HTTP 状态码同时给出；500 不暴露堆栈。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Object>> handleBiz(BizException e) {
        return build(e.code(), e.getMessage(), e.data());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Object>> handleBind(BindException e) {
        List<FieldError> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldError(f.getField(), f.getDefaultMessage()))
                .toList();
        return invalid(fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodValidation(HandlerMethodValidationException e) {
        List<FieldError> fields = e.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream().map(err -> new FieldError(
                        r.getMethodParameter().getParameterName(), err.getDefaultMessage())))
                .toList();
        return invalid(fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraint(ConstraintViolationException e) {
        List<FieldError> fields = e.getConstraintViolations().stream()
                .map(v -> new FieldError(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return invalid(fields);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return invalid(List.of(new FieldError(e.getName(), "格式不正确")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingParam(MissingServletRequestParameterException e) {
        return invalid(List.of(new FieldError(e.getParameterName(), "不能为空")));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingPart(MissingServletRequestPartException e) {
        return invalid(List.of(new FieldError(e.getRequestPartName(), "不能为空")));
    }

    /**
     * 请求体读不出来：JSON 语法错时 fields 为空；某个字段类型不对（整数字段给了小数或字符串等）时，
     * fields 指出字段路径，例如 {@code count}、{@code categoryIds[0]}。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnreadable(HttpMessageNotReadableException e) {
        if (e.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            return invalid(List.of(new FieldError(fieldPath(mismatch), typeHint(mismatch.getTargetType()))));
        }
        return build(ErrorCode.VALIDATION_FAILED, "请求体格式不正确", Map.of("fields", List.of()));
    }

    private static String fieldPath(JsonMappingException e) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference ref : e.getPath()) {
            if (ref.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                path.append('[').append(ref.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    private static String typeHint(Class<?> target) {
        if (target == null) {
            return "格式不正确";
        }
        if (target == Integer.class || target == int.class || target == Long.class || target == long.class
                || target == Short.class || target == short.class) {
            return "必须是整数";
        }
        if (target == Boolean.class || target == boolean.class) {
            return "必须是 true 或 false";
        }
        if (Collection.class.isAssignableFrom(target)) {
            return "必须是数组";
        }
        if (target == String.class) {
            return "必须是字符串";
        }
        return "格式不正确";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleTooLarge(MaxUploadSizeExceededException e) {
        return invalid(List.of(new FieldError("file", "文件太大")));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Object>> handleMultipart(MultipartException e) {
        return invalid(List.of(new FieldError("file", "上传格式不正确")));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return build(ErrorCode.VALIDATION_FAILED, "不支持的 Content-Type", Map.of("fields", List.of()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethod(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(ErrorCode.NOT_FOUND, "不支持的请求方法", null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResource(NoResourceFoundException e) {
        return build(ErrorCode.NOT_FOUND, "接口不存在", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException e) {
        return build(ErrorCode.FORBIDDEN, null, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthentication(AuthenticationException e) {
        return build(ErrorCode.UNAUTHORIZED, null, null);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Object>> handleDuplicate(DuplicateKeyException e) {
        log.warn("唯一约束冲突: {}", e.getMostSpecificCause().getMessage());
        return build(ErrorCode.CONFLICT, "数据已存在", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleOther(Exception e) {
        log.error("未处理的异常", e);
        return build(ErrorCode.INTERNAL_ERROR, null, null);
    }

    private static ResponseEntity<ApiResponse<Object>> invalid(List<FieldError> fields) {
        String message = fields.isEmpty() ? ErrorCode.VALIDATION_FAILED.defaultMessage()
                : fields.get(0).field() + ": " + fields.get(0).message();
        return build(ErrorCode.VALIDATION_FAILED, message, Map.of("fields", fields));
    }

    private static ResponseEntity<ApiResponse<Object>> build(ErrorCode code, String message, Object data) {
        return ResponseEntity.status(code.status())
                .body(ApiResponse.error(code, message == null ? code.defaultMessage() : message, data));
    }

    private static String lastNode(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }
}
