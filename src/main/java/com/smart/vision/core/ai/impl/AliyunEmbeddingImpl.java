package com.smart.vision.core.ai.impl;

import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.smart.vision.core.ai.MultiModalEmbeddingService;
import com.smart.vision.core.manager.BailianEmbeddingManager;
import com.smart.vision.core.model.enums.AliyunErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@Profile("cloud")
@RequiredArgsConstructor
public class AliyunEmbeddingImpl implements MultiModalEmbeddingService {

    private final BailianEmbeddingManager bailianManager;

    /**
     * Get multimodal vector (image)
     *
     * @param imageUrl Image URL
     * @return 1024-dimensional vector
     */
    @Override
    public List<Float> embedImage(String imageUrl) {
        try {
            return bailianManager.embedImage(imageUrl);
        } catch (NoApiKeyException e) {
            log.error(AliyunErrorCode.API_KEY_MISSING.getMessage(), e);
        } catch (ApiException e) {
            log.error(AliyunErrorCode.CALL_FAILED.getMessage(), e);
        } catch (Exception e) {
            log.error(AliyunErrorCode.UNKNOWN.getMessage(), e);
        }
        throw new RuntimeException("embed image failed, try again later.");
    }

    /**
     * Get multimodal vector (text)
     *
     * @param text Text
     * @return 1024-dimensional vector
     */
    @Override
    public List<Float> embedText(String text) {
        try {
            return bailianManager.embedText(text);
        } catch (NoApiKeyException e) {
            log.error(AliyunErrorCode.API_KEY_MISSING.getMessage(), e);
        } catch (ApiException e) {
            log.error(AliyunErrorCode.CALL_FAILED.getMessage(), e);
        } catch (Exception e) {
            log.error(AliyunErrorCode.UNKNOWN.getMessage(), e);
        }
        throw new RuntimeException("embed text failed, try again later.");
    }
}