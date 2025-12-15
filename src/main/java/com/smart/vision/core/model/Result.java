
package com.smart.vision.core.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Unified API response result encapsulation
 * @param <T> Business data type
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Data
public class Result<T> implements Serializable {

    private int code;

    private String message;

    private T data;

    private long timestamp;

    // Private constructor, force use of static methods
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