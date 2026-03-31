package com.smart.vision.core.query.factory;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.smart.vision.core.config.VectorConfig;
import com.smart.vision.core.query.GraphTriplesMatcher;
import com.smart.vision.core.model.dto.HybridSearchParamDTO;
import com.smart.vision.core.query.HybridSearchKeywordMatcher;
import com.smart.vision.core.query.SimilarSearchIdMatcher;
import com.smart.vision.core.query.spec.DuplicateQuerySpec;
import com.smart.vision.core.query.spec.HybridQuerySpec;
import com.smart.vision.core.query.spec.QuerySpec;
import com.smart.vision.core.query.spec.SimilarQuerySpec;
import lombok.RequiredArgsConstructor;
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

    public SearchRequest buildHybrid(HybridSearchParamDTO paramDTO) {
        QuerySpec spec = new HybridQuerySpec(vectorConfig.getReadTargetName(), paramDTO, hybridSearchKeywordMatcher, graphTriplesMatcher);
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
}
