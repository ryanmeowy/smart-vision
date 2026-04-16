
package com.smart.vision.core.integration.ai.port;

/**
 * OCR Recognition Service Interface
 */
public interface OcrService {

  /**
     * Extract text from image
     * @param imageUrl Image URL
     * @return Recognized text content
     */
    String extractText(String imageUrl);
}