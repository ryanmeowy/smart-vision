package com.smart.vision.core.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorTest {

    @Test
    void apiErrorDictionary_shouldExposeStableCodeAndMessage() {
        assertThat(ApiError.INVALID_REQUEST.getCode()).isEqualTo(400);
        assertThat(ApiError.INVALID_REQUEST.getMessage()).isEqualTo("Invalid request parameters.");

        assertThat(ApiError.SEARCH_BACKEND_UNAVAILABLE.getCode()).isEqualTo(500);
        assertThat(ApiError.SEARCH_BACKEND_UNAVAILABLE.getMessage()).isEqualTo("Search backend unavailable");
    }
}
