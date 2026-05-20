package com.zjw.exception;

import lombok.Getter;

/**
 * 业务异常
 *
 * @author ZhangJw
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(500, message);
    }
}
