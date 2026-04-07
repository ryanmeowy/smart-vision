package com.smart.vision.core.ingestion.infrastructure.acl;

import com.smart.vision.core.ingestion.domain.port.IngestionEmbeddingPort;
import com.smart.vision.core.integration.ai.port.MultiModalEmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ACL adapter from ingestion embedding port to integration embedding service.
 */
@Component
@RequiredArgsConstructor
public class EmbeddingIngestionAcl implements IngestionEmbeddingPort {

    private final MultiModalEmbeddingService embeddingService;

    @Override
    public List<Float> embedImage(String imageInput) {
        return embeddingService.embedImage(imageInput);
    }
}
