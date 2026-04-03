package com.smart.vision.core.exception;

import lombok.Getter;

/**
 * Unified API error dictionary for stable code/message mapping.
 */
@Getter
public enum ApiError {
    INVALID_REQUEST(400, "Invalid request parameters."),
    UNAUTHORIZED(401, "Unauthorized access"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Resource not found."),
    CONFLICT(409, "Resource conflict."),
    UPLOAD_TOO_LARGE(400, "The uploaded file is too large, please upload a file within 10MB."),
    SEARCH_BACKEND_UNAVAILABLE(500, "Search backend unavailable"),
    IMAGE_SEARCH_FAILED(500, "Image search failed, please try again later."),
    INTERNAL_ERROR(500, "Internal error, please try again later.");

    private final int code;
    private final String message;

    ApiError(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
