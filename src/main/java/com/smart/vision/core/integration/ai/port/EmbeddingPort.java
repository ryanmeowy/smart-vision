package com.smart.vision.core.integration.ai.port;

import java.util.List;

/**
 * Top-level embedding capability port.
 */
public interface EmbeddingPort {

    List<Float> embedImage(String imageUrl);

    List<Float> embedImage(byte[] imageBytes, String mimeType);

    List<Float> embedText(String text);
}

