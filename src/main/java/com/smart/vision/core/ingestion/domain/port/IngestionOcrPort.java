package com.smart.vision.core.ingestion.domain.port;

/**
 * Domain port for OCR extraction in ingestion.
 */
public interface IngestionOcrPort {

    /**
     * Extract OCR text from image input.
     *
     * @param imageInput image url/data input
     * @return OCR text
     */
    String extractText(String imageInput);
}
