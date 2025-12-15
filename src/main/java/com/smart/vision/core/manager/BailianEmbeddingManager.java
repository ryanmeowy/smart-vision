package com.smart.vision.core.manager;
import com.alibaba.dashscope.embeddings.MultiModalEmbedding;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingParam;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingResult;
import com.google.common.collect.Lists;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class BailianEmbeddingManager {

    public List<Float> embedImage(String imageUrl) {
        try {
            // 1. 创建专用参数对象
            MultiModalEmbeddingParam param = MultiModalEmbeddingParam.builder()
                .model("multimodal-embedding-v1")
                .input(Collections.singletonList(Map.of("image", imageUrl)))
                .build();

            // 2. 创建专用功能对象
            MultiModalEmbedding embedder = new MultiModalEmbedding();
            
            // 3. 调用
            MultiModalEmbeddingResult result = embedder.call(param);
            
            return Collections.singletonList(result.getOutput().getEmbedding().get(0));
        } catch (Exception e) {
            throw new RuntimeException("调用阿里云多模态失败", e);
        }
    }

    public List<Float> embedText(String text) {
        return Lists.newArrayList();
    }
}