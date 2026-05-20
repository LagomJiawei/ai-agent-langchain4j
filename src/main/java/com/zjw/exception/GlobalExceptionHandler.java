package com.zjw.exception;

import com.zjw.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 *
 * @author ZhangJw
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResultUtils<?> handleBusinessException(BusinessException e) {
        log.error("业务异常: {}", e.getMessage());
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResultUtils<?> handleException(Exception e) {
        log.error("系统异常", e);
        return ResultUtils.error("系统繁忙，请稍后重试");
    }
}
