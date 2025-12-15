
package com.smart.vision.core.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一 API 响应结果封装
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> implements Serializable {

    private int code;

    private String message;

    private T data;

    private long timestamp;

    // 私有构造，强制使用静态方法
    private Result() {
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("Success");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    public static <T> Result<T> error(String message) {
        return error(500, message);
    }
}