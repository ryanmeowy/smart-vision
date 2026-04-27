package com.smart.vision.core.search.infrastructure.acl;

import cn.hutool.core.collection.CollectionUtil;
import com.smart.vision.core.integration.multimodal.port.RerankPort;
import com.smart.vision.core.integration.multimodal.port.RerankPort.RerankResult;
import com.smart.vision.core.search.domain.port.SearchRerankPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * ACL adapter that translates search domain rerank port to integration AI rerank service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationSearchRerankAcl implements SearchRerankPort {

    private final RerankPort crossEncoderRerankService;

    @Override
    public List<RerankItem> rerank(String query, List<String> documents, Integer topN) {
        try {
            List<RerankResult> results = crossEncoderRerankService.rerank(query, documents, topN);
            if (CollectionUtil.isEmpty(results)) {
                return List.of();
            }
            return results.stream()
                    .filter(Objects::nonNull)
                    .map(item -> new RerankItem(item.index(), item.score()))
                    .sorted(Comparator.comparingDouble(RerankItem::score).reversed()
                            .thenComparingInt(RerankItem::index))
                    .toList();
        } catch (Exception e) {
            log.warn("Rerank via integration failed, fallback to original order: {}", e.getMessage());
            return List.of();
        }
    }
}
