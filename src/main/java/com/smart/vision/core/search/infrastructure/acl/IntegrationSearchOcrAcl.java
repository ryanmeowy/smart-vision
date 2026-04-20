package com.smart.vision.core.search.infrastructure.acl;

import com.smart.vision.core.integration.ai.port.OcrPort;
import com.smart.vision.core.search.domain.port.SearchOcrPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * ACL adapter from search OCR port to integration OCR service.
 */
@Component
@RequiredArgsConstructor
public class IntegrationSearchOcrAcl implements SearchOcrPort {

    private final OcrPort ocrService;

    @Override
    public String extractText(String imageInput) {
        return ocrService.extractText(imageInput);
    }
}
