package com.smart.vision.core.integration.ai.client.volcengine;

import com.smart.vision.core.common.exception.ApiError;
import com.smart.vision.core.common.exception.InfraException;
import com.volcengine.ark.runtime.model.multimodalembeddings.MultimodalEmbeddingInput;
import com.volcengine.ark.runtime.model.multimodalembeddings.MultimodalEmbeddingRequest;
import com.volcengine.ark.runtime.model.multimodalembeddings.MultimodalEmbeddingResult;
import com.volcengine.ark.runtime.service.ArkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.smart.vision.core.integration.constant.VolcengineConstant.VOLCENGINE_EMBEDDING_MODEL_NAME;

@Slf4j
@Component
@RequiredArgsConstructor
public class VolcengineEmbeddingManager {

    private final ArkService arkService;

    @Retryable(
            retryFor = {Exception.class},
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public List<Float> embedImage(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return Collections.emptyList();
        }
        MultimodalEmbeddingInput input = MultimodalEmbeddingInput.builder()
                .type("image_url")
                .imageUrl(new MultimodalEmbeddingInput.MultiModalEmbeddingContentPartImageURL(imageUrl))
                .build();
        return callSdk(List.of(input));
    }

    @Retryable(
            retryFor = {Exception.class},
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public List<Float> embedText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        MultimodalEmbeddingInput input = MultimodalEmbeddingInput.builder()
                .text(text)
                .build();
        return callSdk(List.of(input));
    }

    private List<Float> callSdk(List<MultimodalEmbeddingInput> inputs) {
        MultimodalEmbeddingRequest multiModalEmbeddingRequest = MultimodalEmbeddingRequest.builder()
                .model(VOLCENGINE_EMBEDDING_MODEL_NAME)
                .input(inputs)
                .build();

        MultimodalEmbeddingResult res = arkService.createMultiModalEmbeddings(multiModalEmbeddingRequest);

        if (null == res || null == res.getData()) {
            log.info("embedding failed, request:{}, response:{}", inputs, res);
            throw new InfraException(ApiError.EMBEDDING_FAILED);
        }

        List<Double> rawEmbeddingList = CollectionUtils.isEmpty(res.getData().getEmbedding()) ? Collections.emptyList() : res.getData().getEmbedding();

        return rawEmbeddingList.stream()
                .filter(Objects::nonNull)
                .map(Double::floatValue)
                .collect(Collectors.toList());
    }

}
