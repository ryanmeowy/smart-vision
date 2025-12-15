package com.smart.vision.core.manager;

import com.alibaba.dashscope.embeddings.MultiModalEmbedding;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingParam;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingResult;
import com.google.common.collect.Lists;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Bailian embedding manager for handling multimodal embeddings
 * This manager integrates with Alibaba Cloud's Bailian service to generate
 * vector representations of images and text for semantic search capabilities
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Component
public class BailianEmbeddingManager {

    public List<Float> embedImage(String imageUrl) {
        try {
            MultiModalEmbeddingParam param = MultiModalEmbeddingParam.builder()
                    .model("multimodal-embedding-v1")
//                    .input(Collections.singletonList(Map.of("image", imageUrl)))
                    .build();

            MultiModalEmbedding embedder = new MultiModalEmbedding();
            MultiModalEmbeddingResult result = embedder.call(param);
            return result.getOutput().getEmbedding().stream()
                    .map(Double::floatValue)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to call Alibaba Cloud multimodal service", e);
        }
    }

    public List<Float> embedText(String text) {
        return Lists.newArrayList();
    }
}