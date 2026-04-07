package com.smart.vision.core.ingestion.domain.port;

/**
 * Domain port for object storage operations used by ingestion.
 */
public interface IngestionObjectStoragePort {

    /**
     * Build temporary image input for AI embedding/OCR/captioning.
     *
     * @param objectKey object storage key
     * @return temporary accessible image input
     */
    String buildAiImageInput(String objectKey);
}
