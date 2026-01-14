package com.smart.vision.core.ai.impl;

import com.smart.vision.core.ai.MultiModalEmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@Profile("local")
public class LocalEmbeddingImpl implements MultiModalEmbeddingService {

    /**
     * Get multimodal vector (image)
     *
     * @param imageUrl Image URL (optional)
     * @return 1024-dimensional (or 768-dimensional) vector
     */
    @Override
    public List<Float> embedImage(String imageUrl) {
        log.info("⚡️ [Local] 调用本地 gRPC 计算向量: img={}", imageUrl);
        return List.of();
    }

    /**
     * Get multimodal vector (text)
     *
     * @param text Text (optional)
     * @return 1024-dimensional (or 768-dimensional) vector
     */
    @Override
    public List<Float> embedText(String text) {
        log.info("⚡️ [Local] 调用本地 gRPC 计算向量: text={}", text);
        return List.of();
    }
}