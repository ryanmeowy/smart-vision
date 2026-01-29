package com.smart.vision.core.manager;

import com.alibaba.dashscope.embeddings.MultiModalEmbedding;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingItemBase;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingItemImage;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingItemText;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingParam;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingResult;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingResultItem;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.google.common.collect.Lists;
import com.smart.vision.core.util.VectorUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.smart.vision.core.constant.AliyunConstant.EMBEDDING_MODEL_NAME;

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

    @Value("${DASHSCOPE_API_KEY}")
    private String apiKey;

    /**
     * call Aliyun to generate vectors,
     * it automatically retries up to 3 times, with intervals of 1 second, 2 seconds, and 4 seconds (multiplier=2) between attempts.
     */
    @Retryable(
            retryFor = {Exception.class},
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public List<Float> embedImage(String imageUrl) throws NoApiKeyException, UploadFileException, ApiException {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return Collections.emptyList();
        }
        MultiModalEmbeddingItemBase item = new MultiModalEmbeddingItemImage(imageUrl);
        return VectorUtil.l2Normalize(callSdk(Lists.newArrayList(item)));
    }

    @Retryable(
            retryFor = {Exception.class},
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public List<Float> embedText(String text) throws NoApiKeyException, UploadFileException {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        MultiModalEmbeddingItemBase item = new MultiModalEmbeddingItemText(text);
        return VectorUtil.l2Normalize(callSdk(Lists.newArrayList(item)));
    }

    private List<Float> callSdk(List<MultiModalEmbeddingItemBase> inputItem) throws NoApiKeyException, UploadFileException {
        MultiModalEmbeddingParam param = MultiModalEmbeddingParam.builder()
                .apiKey(apiKey)
                .model(EMBEDDING_MODEL_NAME)
                .contents(inputItem)
                .build();

        MultiModalEmbedding embedder = new MultiModalEmbedding();
        MultiModalEmbeddingResult result = embedder.call(param);

        if (result.getOutput() == null || CollectionUtils.isEmpty(result.getOutput().getEmbeddings())) {
            throw new RuntimeException("Aliyun returned an empty result");
        }

        return result.getOutput().getEmbeddings().stream()
                .findFirst()
                .map(MultiModalEmbeddingResultItem::getEmbedding)
                .orElse(Collections.emptyList())
                .stream()
                .map(Double::floatValue)
                .collect(Collectors.toList());
    }


}