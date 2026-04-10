package com.smart.vision.core.common.exception;

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
    AUTH_TOKEN_INVALID(401, "The token is invalid or expired, please contact the administrator to refresh it"),
    AUTH_ADMIN_SECRET_MISSING(500, "Internal error"),
    AUTH_STS_FETCH_FAILED(500, "Failed to fetch STS token"),
    IMAGE_UPLOAD_REQUIRED(400, "Please upload an image"),
    UPLOAD_TOO_LARGE(400, "The uploaded file is too large, please upload a file within 10MB."),
    VECTOR_COMPARE_FAILED(500, "Vector compare failed, please try again later."),
    EMBEDDING_FAILED(500, "Failed to generate image embedding, please retry later."),
    EMBEDDING_RESULT_EMPTY(500, "Embedding result is empty, please retry later."),
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
