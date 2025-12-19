
package com.smart.vision.core.model.enums;

/**
 * Search strategy type enumeration
 *
 * @author Ryan
 * @since 2025/12/15
 */
public enum StrategyTypeEnum {
    /**
     * Hybrid search (default): vector similarity + OCR text matching
     * Use case: general search, searching for both visual content and text
     */
    HYBRID,

    /**
     * Pure vector search
     * Use case: text-to-image search (describing visual content), ignoring OCR
     */
    VECTOR_ONLY,

    /**
     * Pure text search (BM25)
     * Use case: searching only for text within images
     */
    TEXT_ONLY,

    /**
     * Image-to-image search
     * Use case: user uploads an image to search for similar images
     */
    IMAGE_TO_IMAGE;
}