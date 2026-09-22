package com.helianhealth.iflow.console.config;

import com.helianhealth.iflow.common.base.ResultData;
import com.helianhealth.iflow.common.exception.WorkFlowException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class ConsoleExceptionHandler {

    @ExceptionHandler(WorkFlowException.class)
    public ResponseEntity<ResultData<?>> handleAgentException(WorkFlowException ex) {
        log.warn("Business exception: code={}, message={}", ex.getErrorCode(), ex.getErrorMessage(), ex);
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getErrorMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResultData<?>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Illegal argument", ex);
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage() == null || ex.getMessage().isBlank() ? "请求参数错误" : ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResultData<?>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex) {
        String message =
                ex.getBindingResult().getFieldError() != null
                        ? ex.getBindingResult().getFieldError().getDefaultMessage()
                        : "请求参数校验失败";
        log.warn("Validation failed: {}", message, ex);
        return buildResponse(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.value(), message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResultData<?>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException ex) {
        String message = "缺少必填参数: " + ex.getParameterName();
        log.warn("Missing parameter: {}", ex.getParameterName(), ex);
        return buildResponse(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.value(), message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResultData<?>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex) {
        log.warn("Request body parse failed", ex);
        return buildResponse(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.value(), "请求体格式错误");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFoundException(NoResourceFoundException ex) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResultData<?>> handleSystemException(Exception ex) {
        log.error("Unhandled exception", ex);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "系统异常，请联系管理员");
    }

    private ResponseEntity<ResultData<?>> buildResponse(HttpStatus status, int code, String message) {
        return new ResponseEntity<>(new ResultData<>(code, message, null), status);
    }
}
