package com.helianhealth.iflow.gateway.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/** 将 Controller 调用前发生的 multipart 错误统一为 Open API 状态码。 */
@RestControllerAdvice
public class OpenApiMultipartExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> tooLarge(
            MaxUploadSizeExceededException exception, HttpServletRequest request) {
        requireOpenApi(request, exception);
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "请求体超过大小限制");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, Object>> invalidMultipart(
            MultipartException exception, HttpServletRequest request) {
        requireOpenApi(request, exception);
        if (hasSizeCause(exception)) {
            return response(HttpStatus.PAYLOAD_TOO_LARGE, "请求体超过大小限制");
        }
        return response(HttpStatus.BAD_REQUEST, "multipart/form-data 请求不合法");
    }

    private static void requireOpenApi(HttpServletRequest request, MultipartException exception) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!(path.equals("/hl-open-api") || path.startsWith("/hl-open-api/"))) {
            throw exception;
        }
    }

    private static boolean hasSizeCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof OpenApiPayloadTooLargeException
                    || current instanceof MaxUploadSizeExceededException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code", status.value(), "message", message));
    }
}
