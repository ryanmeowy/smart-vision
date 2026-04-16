package com.smart.vision.core.ingestion.infrastructure.acl;

import com.smart.vision.core.ingestion.domain.port.IngestionOcrPort;
import com.smart.vision.core.integration.ai.port.OcrService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * ACL adapter from ingestion OCR port to integration OCR service.
 */
@Component
@RequiredArgsConstructor
public class OcrIngestionAcl implements IngestionOcrPort {

    private final OcrService ocrService;

    @Override
    public String extractText(String imageInput) {
        return ocrService.extractText(imageInput);
    }
}
