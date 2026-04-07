package com.smart.vision.core.search.infrastructure.acl;

import cn.hutool.core.collection.CollectionUtil;
import com.smart.vision.core.integration.ai.port.ContentGenerationService;
import com.smart.vision.core.search.domain.model.GraphTriple;
import com.smart.vision.core.search.domain.port.QueryGraphParserPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ACL adapter that translates search domain graph-parse port to integration AI service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationQueryGraphParserAcl implements QueryGraphParserPort {

    private final ContentGenerationService contentGenerationService;

    @Override
    public List<GraphTriple> parseFromKeyword(String keyword) {
        try {
            List<GraphTriple> triples = contentGenerationService.praseTriplesFromKeyword(keyword);
            return CollectionUtil.isEmpty(triples) ? List.of() : triples;
        } catch (Exception e) {
            log.warn("Graph parse via integration failed, fallback to empty triples: {}", e.getMessage());
            return List.of();
        }
    }
}
