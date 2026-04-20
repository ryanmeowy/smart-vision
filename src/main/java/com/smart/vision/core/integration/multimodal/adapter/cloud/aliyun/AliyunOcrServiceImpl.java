
package com.smart.vision.core.integration.multimodal.adapter.cloud.aliyun;

import com.alibaba.dashscope.exception.NoApiKeyException;
import com.smart.vision.core.integration.multimodal.port.OcrPort;
import com.smart.vision.core.integration.multimodal.client.aliyun.AliyunOcrManager;
import com.smart.vision.core.integration.multimodal.domain.model.AliyunErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.capability-provider", name = "ocr", havingValue = "aliyun")
public class AliyunOcrServiceImpl implements OcrPort {

    private final AliyunOcrManager ocrManager;

    @Override
    public String extractText(String imageUrl) {
        try {
            return ocrManager.llmOcrContent(imageUrl);
        } catch (NoApiKeyException e) {
            log.error(AliyunErrorCode.API_KEY_MISSING.getMessage(), e);
        } catch (Exception e) {
            log.warn(AliyunErrorCode.UNKNOWN.getMessage(), e);
        }
        throw new RuntimeException("OCR failed, try again later.");
    }
}
