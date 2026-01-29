package com.helianhealth.agent.console.config;

import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.exception.BaseBusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 处理业务异常（BaseBusinessException 及其子类）
    @ExceptionHandler(BaseBusinessException.class)
    public ResponseEntity<ResultData<?>> handleBusinessException(BaseBusinessException ex) {
        log.warn("Business exception occurred: code={}, message={}", ex.getBusinessCode().getCode(), ex.getMessage());
        ResultData<?> resultData = ResultData.builder()
                .code(ex.getBusinessCode().getCode())
                .message(ex.getMessage())
                .rsp(null) // 业务异常通常数据返回 null，也可按需处理
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
                .code(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value())) // 系统异常返回 500
                .message(ex.getMessage() != null ? ex.getMessage() : "Internal Server Error")
                .rsp(null)
                .build();

        return new ResponseEntity<>(resultData, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}