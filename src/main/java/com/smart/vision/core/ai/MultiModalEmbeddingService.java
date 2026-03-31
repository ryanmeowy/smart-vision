package com.smart.vision.core.ai;

import java.util.List;

/**
 * Multimodel Vectorization Service Interface
 * Abstracts the differences between Aliyun DashScope and local Python/CLIP implementations
 */
public interface MultiModalEmbeddingService {

    /**
     * Get multimodal vector (image)
     *
     * @param imageUrl Image URL
     * @return vector
     */
    List<Float> embedImage(String imageUrl);

    /**
     * Get multimodal vector (image bytes).
     *
     * @param imageBytes image bytes
     * @param mimeType image mime type, e.g. image/jpeg
     * @return vector
     */
    List<Float> embedImage(byte[] imageBytes, String mimeType);

    /**
     * Get multimodal vector (text)
     *
     * @param text Text
     * @return vector
     */
    List<Float> embedText(String text);
}
