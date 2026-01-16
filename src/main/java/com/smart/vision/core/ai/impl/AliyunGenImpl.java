package com.smart.vision.core.ai.impl;

import com.smart.vision.core.ai.ContentGenerationService;
import com.smart.vision.core.manager.AliyunGenManager;
import com.smart.vision.core.manager.AliyunTaggingManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "cloud", matchIfMissing = true)
@RequiredArgsConstructor
public class AliyunGenImpl implements ContentGenerationService {

    private final AliyunGenManager genManager;
    private final AliyunTaggingManager taggingManager;

    @Override
    public SseEmitter streamGenerateCopy(String imageUrl, String promptType) {
        return genManager.streamGenerateCopy(imageUrl, promptType);
    }

    @Override
    public String generateFileName(String imageUrl) {
        return genManager.genFileName(imageUrl);
    }

    @Override
    public List<String> generateTags(String imageUrl) {
        return taggingManager.generateTags(imageUrl);
    }
}