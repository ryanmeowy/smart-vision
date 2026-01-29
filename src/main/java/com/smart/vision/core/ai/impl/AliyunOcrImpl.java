
package com.smart.vision.core.ai.impl;

import com.smart.vision.core.ai.ImageOcrService;
import com.smart.vision.core.manager.AliyunOcrManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("cloud")@RequiredArgsConstructor
public class AliyunOcrImpl implements ImageOcrService {

    private final AliyunOcrManager ocrManager;

    @Override
    public String extractText(String imageUrl) {
        return ocrManager.llmOcrContent(imageUrl);
    }
}