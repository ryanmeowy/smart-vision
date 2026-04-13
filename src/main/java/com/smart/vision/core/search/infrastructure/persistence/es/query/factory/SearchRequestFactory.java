package com.smart.vision.core.search.infrastructure.persistence.es.query.factory;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.smart.vision.core.common.config.VectorConfig;
import com.smart.vision.core.search.infrastructure.persistence.es.query.GraphTriplesMatcher;
import com.smart.vision.core.search.domain.model.HybridSearchParamDTO;
import com.smart.vision.core.search.infrastructure.persistence.es.query.HybridSearchKeywordMatcher;
import com.smart.vision.core.search.infrastructure.persistence.es.query.SimilarSearchIdMatcher;
import com.smart.vision.core.search.infrastructure.persistence.es.query.spec.DuplicateQuerySpec;
import com.smart.vision.core.search.infrastructure.persistence.es.query.spec.HybridQuerySpec;
import com.smart.vision.core.search.infrastructure.persistence.es.query.spec.HybridNativeRrfQuerySpec;
import com.smart.vision.core.search.infrastructure.persistence.es.query.spec.QuerySpec;
import com.smart.vision.core.search.infrastructure.persistence.es.query.spec.SimilarQuerySpec;
import com.smart.vision.core.search.infrastructure.persistence.es.query.spec.TextOnlyQuerySpec;
import com.smart.vision.core.search.infrastructure.persistence.es.query.spec.VectorOnlyQuerySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Factory that compiles QuerySpec into Elasticsearch SearchRequest.
 * <p>
 * Phase 1: factory/spec classes are introduced but repository traffic is not switched yet.
 */
@Component
@RequiredArgsConstructor
public class SearchRequestFactory {

    private final VectorConfig vectorConfig;
    private final HybridSearchKeywordMatcher hybridSearchKeywordMatcher;
    private final SimilarSearchIdMatcher similarSearchIdMatcher;
    private final GraphTriplesMatcher graphTriplesMatcher;
    @Value("${app.search.vector-min-score:0.6}")
    private double vectorMinScore;

    public SearchRequest buildHybrid(HybridSearchParamDTO paramDTO) {
        QuerySpec spec = new HybridQuerySpec(vectorConfig.getReadTargetName(), paramDTO, hybridSearchKeywordMatcher, graphTriplesMatcher);
        return spec.toSearchRequest();
    }

    public SearchRequest buildHybridNativeRrf(HybridSearchParamDTO paramDTO,
                                              Integer rankConstant,
                                              Integer rankWindowSize) {
        QuerySpec spec = new HybridNativeRrfQuerySpec(
                vectorConfig.getReadTargetName(),
                paramDTO,
                hybridSearchKeywordMatcher,
                graphTriplesMatcher,
                rankConstant,
                rankWindowSize
        );
        return spec.toSearchRequest();
    }

    public SearchRequest buildSimilar(List<Float> vector, Integer topK, String excludeDocId) {
        QuerySpec spec = new SimilarQuerySpec(vectorConfig.getReadTargetName(), vector, topK, excludeDocId, similarSearchIdMatcher);
        return spec.toSearchRequest();
    }

    public SearchRequest buildDuplicate(List<Float> vector, double threshold) {
        QuerySpec spec = new DuplicateQuerySpec(vectorConfig.getReadTargetName(), vector, threshold);
        return spec.toSearchRequest();
    }

    public SearchRequest buildVectorOnly(List<Float> vector, Integer topK) {
        QuerySpec spec = new VectorOnlyQuerySpec(vectorConfig.getReadTargetName(), vector, topK, vectorMinScore);
        return spec.toSearchRequest();
    }

    public SearchRequest buildTextOnly(String keyword, Integer limit, Boolean enableOcr) {
        QuerySpec spec = new TextOnlyQuerySpec(vectorConfig.getReadTargetName(), keyword, limit, enableOcr, hybridSearchKeywordMatcher);
        return spec.toSearchRequest();
    }
}
