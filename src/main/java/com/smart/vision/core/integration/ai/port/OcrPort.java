package com.smart.vision.core.integration.ai.port;

/**
 * Top-level OCR capability port.
 */
public interface OcrPort {

    String extractText(String imageUrl);
}

