package com.zjw.common;

import lombok.Data;

/**
 * 统一响应结果
 *
 * @author ZhangJw
 */
@Data
public class ResultUtils<T> {

    private int code;
    private String message;
    private T data;

    public static <T> ResultUtils<T> success(T data) {
        ResultUtils<T> result = new ResultUtils<>();
        result.code = 200;
        result.message = "success";
        result.data = data;
        return result;
    }

    public static <T> ResultUtils<T> success() {
        return success(null);
    }

    public static <T> ResultUtils<T> error(int code, String message) {
        ResultUtils<T> result = new ResultUtils<>();
        result.code = code;
        result.message = message;
        return result;
    }

    public static <T> ResultUtils<T> error(String message) {
        return error(500, message);
    }
}
