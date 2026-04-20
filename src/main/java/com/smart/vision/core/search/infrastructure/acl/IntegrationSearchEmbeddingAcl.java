package com.smart.vision.core.search.infrastructure.acl;

import com.smart.vision.core.integration.ai.port.EmbeddingPort;
import com.smart.vision.core.search.domain.port.SearchEmbeddingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ACL adapter from search embedding port to integration embedding service.
 */
@Component
@RequiredArgsConstructor
public class IntegrationSearchEmbeddingAcl implements SearchEmbeddingPort {

    private final EmbeddingPort embeddingService;

    @Override
    public List<Float> embedText(String text) {
        return embeddingService.embedText(text);
    }

    @Override
    public List<Float> embedImage(String imageInput) {
        return embeddingService.embedImage(imageInput);
    }

    @Override
    public List<Float> embedImage(byte[] imageBytes, String contentType) {
        return embeddingService.embedImage(imageBytes, contentType);
    }
}
