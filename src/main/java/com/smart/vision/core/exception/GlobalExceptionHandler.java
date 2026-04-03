package com.smart.vision.core.exception;

import com.smart.vision.core.model.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.UUID;

/**
 * Global API exception handler for consistent error responses and observability.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        return Result.error(400, safeMessage(e.getMessage(), "Invalid request parameters."));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public Result<Void> handleBadRequest(Exception e) {
        return Result.error(400, "Invalid request parameters.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return Result.error(400, "The uploaded file is too large, please upload a file within 10MB.");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnexpected(Exception e, HttpServletRequest request) {
        String path = request == null ? "unknown" : request.getRequestURI();
        String errorId = UUID.randomUUID().toString();
        log.error("Unhandled exception, errorId={}, path={}, message={}", errorId, path, e.getMessage(), e);
        return Result.error(500, "Internal error, please try again later.");
    }

    private String safeMessage(String message, String fallback) {
        return StringUtils.hasText(message) ? message : fallback;
    }
}
