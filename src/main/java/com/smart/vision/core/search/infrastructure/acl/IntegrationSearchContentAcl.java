package com.smart.vision.core.search.infrastructure.acl;

import cn.hutool.core.collection.CollectionUtil;
import com.smart.vision.core.common.model.GraphTriple;
import com.smart.vision.core.integration.multimodal.port.GenPort;
import com.smart.vision.core.search.domain.port.SearchContentPort;
import com.smart.vision.core.search.interfaces.rest.dto.GraphTripleDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * ACL adapter from search content port to integration content service.
 */
@Component
@RequiredArgsConstructor
public class IntegrationSearchContentAcl implements SearchContentPort {

    private final GenPort contentGenerationService;

    @Override
    public String generateSummary(String imageInput) {
        return contentGenerationService.generateSummary(imageInput);
    }

    @Override
    public List<String> generateTags(String imageInput) {
        List<String> tags = contentGenerationService.generateTags(imageInput);
        return CollectionUtil.isEmpty(tags) ? List.of() : tags;
    }

    @Override
    public List<GraphTripleDTO> generateGraph(String imageInput) {
        List<GraphTriple> triples = contentGenerationService.generateGraph(imageInput);
        if (CollectionUtil.isEmpty(triples)) {
            return List.of();
        }
        return triples.stream()
                .filter(Objects::nonNull)
                .map(t -> new GraphTripleDTO(t.getS(), t.getP(), t.getO()))
                .toList();
    }
}
