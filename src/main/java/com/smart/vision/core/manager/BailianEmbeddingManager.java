package com.smart.vision.core.manager;

import com.alibaba.dashscope.embeddings.MultiModalEmbedding;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingParam;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingResult;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Bailian embedding manager for handling multimodal embeddings
 * This manager integrates with Aliyun Bailian service to generate
 * vector representations of images and text for semantic search capabilities
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Slf4j
@Component
public class BailianEmbeddingManager {

    /**
     * call Aliyun to generate vectors,
     * it automatically retries up to 3 times, with intervals of 1 second, 2 seconds, and 4 seconds (multiplier=2) between attempts.
     */
    @Retryable(
            retryFor = {Exception.class},
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public List<Float> embedImage(String imageUrl) throws NoApiKeyException, UploadFileException {
        MultiModalEmbeddingParam param = MultiModalEmbeddingParam.builder()
                .model("multimodal-embedding-v1")
//                    .input(Collections.singletonList(Map.of("image", imageUrl)))
                .build();

        MultiModalEmbedding embedder = new MultiModalEmbedding();
        MultiModalEmbeddingResult result = embedder.call(param);
        return result.getOutput().getEmbedding().stream()
                .map(Double::floatValue)
                .collect(Collectors.toList());
    }

    public List<Float> embedText(String text) {
        return Lists.newArrayList();
    }


    @Recover
    public List<Float> recover(Exception e, String imageUrl) throws Exception {
        log.error("After retrying 3 times, the AI service still failed.: {}", imageUrl);
        throw e;
    }


}