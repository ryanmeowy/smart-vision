package com.smart.vision.core.ai.impl;

import com.smart.vision.core.ai.MultiModalEmbeddingService;
import com.smart.vision.core.manager.BailianEmbeddingManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "cloud", matchIfMissing = true)
@RequiredArgsConstructor
public class AliyunEmbeddingImpl implements MultiModalEmbeddingService {

    private final BailianEmbeddingManager bailianManager;

    /**
     * Get multimodal vector (image)
     *
     * @param imageUrl Image URL (optional)
     * @return 1024-dimensional vector
     */
    @Override
    public List<Float> embedImage(String imageUrl) {
        return bailianManager.embedImage(imageUrl);
    }

    /**
     * Get multimodal vector (text)
     *
     * @param text Text (optional)
     * @return 1024-dimensional vector
     */
    @Override
    public List<Float> embedText(String text) {
        return bailianManager.embedText(text);
    }
}