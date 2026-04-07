package com.smart.vision.core.common.exception;

import lombok.Getter;

/**
 * Unified business exception carrying an explicit API error code.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ApiError error) {
        this(error.getCode(), error.getMessage());
    }

    public BusinessException(ApiError error, String message) {
        this(error.getCode(), message);
    }
}
