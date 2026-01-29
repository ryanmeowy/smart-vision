
package com.smart.vision.core.ai.impl;

import com.alibaba.dashscope.exception.NoApiKeyException;
import com.smart.vision.core.ai.ImageOcrService;
import com.smart.vision.core.manager.AliyunOcrManager;
import com.smart.vision.core.model.enums.AliyunErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("cloud")
@RequiredArgsConstructor
public class AliyunOcrImpl implements ImageOcrService {

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