
package com.smart.vision.core.ai;

/**
 * OCR Recognition Service Interface
 */
public interface ImageOcrService {

  /**
     * Extract text from image
     * @param imageUrl Image URL
     * @return Recognized text content
     */
    String extractText(String imageUrl);
}