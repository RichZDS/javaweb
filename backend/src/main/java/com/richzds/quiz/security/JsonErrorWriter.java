package com.richzds.quiz.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.richzds.quiz.common.ApiResponse;
import com.richzds.quiz.common.ErrorCode;

/** 安全层（过滤器链里，进不了 Controller）的 401 / 403 也按统一格式返回。 */
@Component
public class JsonErrorWriter implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage());
    }

    @Override
    public void handle(jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.defaultMessage());
    }

    public void write(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(code, message, null));
    }
}
