package com.helianhealth.agent.console.config;

import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.exception.AgentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 处理框架业务异常（AgentException 及其子类）
    @ExceptionHandler(AgentException.class)
    public ResponseEntity<ResultData<?>> handleAgentException(AgentException ex) {
        log.warn("Agent exception occurred: code={}, message={}", ex.getErrorCode().getCode(), ex.getMessage());
        ResultData<?> resultData = ResultData.builder()
                .code(String.valueOf(ex.getErrorCode().getCode()))
                .message(ex.getErrorCode().getMessage())
                .rsp(null)
                .build();
        return new ResponseEntity<>(resultData, HttpStatus.BAD_REQUEST);
    }

    // 处理 IllegalArgumentException（参数校验异常）
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResultData<?>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Illegal argument exception: {}", ex.getMessage());
        ResultData<?> resultData = ResultData.builder()
                .code(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                .message(ex.getMessage())
                .rsp(null)
                .build();
        return new ResponseEntity<>(resultData, HttpStatus.BAD_REQUEST);
    }

    // 处理系统异常（其他未捕获的异常）
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResultData<?>> handleSystemException(Exception ex) {
        log.error("System exception occurred", ex);
        ResultData<?> resultData = ResultData.builder()
                .code(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()))
                .message(ex.getMessage() != null ? ex.getMessage() : "Internal Server Error")
                .rsp(null)
                .build();

        return new ResponseEntity<>(resultData, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}