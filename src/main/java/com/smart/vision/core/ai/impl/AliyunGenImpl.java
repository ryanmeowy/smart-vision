package com.smart.vision.core.ai.impl;

import com.smart.vision.core.ai.ContentGenerationService;
import com.smart.vision.core.manager.AliyunGenManager;
import com.smart.vision.core.model.dto.GraphTripleDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Service
@Profile("cloud")
@RequiredArgsConstructor
public class AliyunGenImpl implements ContentGenerationService {

    private final AliyunGenManager genManager;

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
        return genManager.generateTags(imageUrl);
    }

    /**
     * Generate graph for the image
     *
     * @param imageUrl Image URL
     * @return List of graph triples
     */
    @Override
    public List<GraphTripleDTO> generateGraph(String imageUrl) {
        return genManager.generateGraph(imageUrl);
    }

    @Override
    public List<GraphTripleDTO> praseTriplesFromKeyword(String keyword) {
        return genManager.praseTriplesFromKeyword(keyword);
    }
}