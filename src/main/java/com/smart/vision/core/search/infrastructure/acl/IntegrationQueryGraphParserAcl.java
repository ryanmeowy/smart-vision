package com.smart.vision.core.search.infrastructure.acl;

import cn.hutool.core.collection.CollectionUtil;
import com.smart.vision.core.common.model.GraphTriple;
import com.smart.vision.core.integration.ai.port.GenPort;
import com.smart.vision.core.search.domain.port.QueryGraphParserPort;
import com.smart.vision.core.search.interfaces.rest.dto.GraphTripleDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * ACL adapter that translates search domain graph-parse port to integration AI service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationQueryGraphParserAcl implements QueryGraphParserPort {

    private final GenPort contentGenerationService;

    @Override
    public List<GraphTripleDTO> parseFromKeyword(String keyword) {
        try {
            List<GraphTriple> triples = contentGenerationService.praseTriplesFromKeyword(keyword);
            if (CollectionUtil.isEmpty(triples)) {
                return List.of();
            }
            return triples.stream()
                    .filter(Objects::nonNull)
                    .map(t -> new GraphTripleDTO(t.getS(), t.getP(), t.getO()))
                    .toList();
        } catch (Exception e) {
            log.warn("Graph parse via integration failed, fallback to empty triples: {}", e.getMessage());
            return List.of();
        }
    }
}
