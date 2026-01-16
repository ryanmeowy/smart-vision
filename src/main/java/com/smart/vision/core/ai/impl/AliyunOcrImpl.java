
package com.smart.vision.core.ai.impl;

import com.smart.vision.core.ai.ImageOcrService;
import com.smart.vision.core.manager.AliyunOcrManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "cloud", matchIfMissing = true)
@RequiredArgsConstructor
public class AliyunOcrImpl implements ImageOcrService {

    private final AliyunOcrManager ocrManager;

    @Override
    public String extractText(String imageUrl) {
        return ocrManager.llmOcrContent(imageUrl);
    }
}