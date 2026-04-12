package com.smart.vision.core.common.exception;

/**
 * Infrastructure-level business exception for backend dependency failures.
 */
public class InfraException extends BusinessException {

    public InfraException(String message) {
        super(ApiError.INTERNAL_ERROR, message);
    }

    public InfraException(ApiError error) {
        super(error);
    }

    public InfraException(ApiError error, String message) {
        super(error, message);
    }

    public InfraException(ApiError error, Throwable cause) {
        super(error, cause);
    }

    public InfraException(ApiError error, String message, Throwable cause) {
        super(error, message, cause);
    }
}
