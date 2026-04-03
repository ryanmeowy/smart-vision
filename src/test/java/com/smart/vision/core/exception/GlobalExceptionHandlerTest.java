package com.smart.vision.core.exception;

import com.smart.vision.core.model.Result;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleIllegalArgument_shouldReturn400WithMessage() {
        Result<Void> result = handler.handleIllegalArgument(new IllegalArgumentException("bad input"));

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).isEqualTo("bad input");
    }

    @Test
    void handleUnexpected_shouldReturn500WithoutLeakingInternalMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/vision/search");

        Result<Void> result = handler.handleUnexpected(new RuntimeException("db timeout"), request);

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).isEqualTo("Internal error, please try again later.");
    }
}
