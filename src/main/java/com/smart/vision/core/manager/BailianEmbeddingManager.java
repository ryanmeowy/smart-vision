package com.smart.vision.core.manager;

import com.alibaba.dashscope.embeddings.MultiModalEmbedding;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingItemBase;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingItemImage;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingItemText;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingParam;
import com.alibaba.dashscope.embeddings.MultiModalEmbeddingResult;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.smart.vision.core.constant.CommonConstant.MODEL_NAME;

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
            retryFor = {RuntimeException.class},
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public List<Float> embedImage(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return Collections.emptyList();
        }
        MultiModalEmbeddingItemBase item = new MultiModalEmbeddingItemImage(imageUrl);
        return callSdk(Lists.newArrayList(item));
    }

    @Retryable(
            retryFor = {RuntimeException.class},
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public List<Float> embedText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        MultiModalEmbeddingItemBase item = new MultiModalEmbeddingItemText(text);
        return callSdk(Lists.newArrayList(item));
    }


    @Recover
    public List<Float> recover(Exception e, String imageUrl) throws Exception {
        log.error("After retrying 3 times, the AI service still failed.: {}", imageUrl);
        throw e;
    }

    private List<Float> callSdk(List<MultiModalEmbeddingItemBase> inputItem) {
        try {
            MultiModalEmbeddingParam param = MultiModalEmbeddingParam.builder()
                    .apiKey(apiKey)
                    .model(MODEL_NAME)
                    .contents(inputItem)
                    .build();

            MultiModalEmbedding embedder = new MultiModalEmbedding();
            MultiModalEmbeddingResult result = embedder.call(param);

            if (result.getOutput() == null || result.getOutput().getEmbedding().isEmpty()) {
                throw new RuntimeException("阿里云返回结果为空");
            }

            return result.getOutput().getEmbedding().stream().map(Double::floatValue).collect(Collectors.toList());
        } catch (NoApiKeyException e) {
            log.error("API Key 未配置，请检查环境变量");
            throw new RuntimeException("API Key 配置缺失", e);
        } catch (ApiException e) {
            log.error("阿里云 API 调用失败: Code={}, Msg={}", e.getStatus(), e.getMessage());
            throw new RuntimeException("AI服务异常: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("向量生成未知错误", e);
            throw new RuntimeException("向量化服务内部错误", e);
        }
    }


}