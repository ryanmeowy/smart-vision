package com.smart.vision.core.repository.query;

import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.util.ObjectBuilder;
import com.smart.vision.core.model.dto.SearchQueryDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

import static com.smart.vision.core.constant.CommonConstant.DEFAULT_NUM_CANDIDATES;
import static com.smart.vision.core.constant.CommonConstant.HYBRID_SEARCH_DEFAULT_MIN_SCORE;

/**
 * Query component for building KNN vector search queries
 *
 * @author ryan
 * @since 2025/12/23
 */
@Component
public class KnnQueryComponent implements QueryComponent<KnnSearch.Builder, KnnSearch> {

    public Function<KnnSearch.Builder, ObjectBuilder<KnnSearch>> buildQuery(SearchQueryDTO query, List<Float> queryVector) {
        return k -> k
                .field("imageEmbedding")
                .queryVector(queryVector)
                .k(query.getLimit())
                .numCandidates(DEFAULT_NUM_CANDIDATES)
                .boost(0.9f)
                .similarity(null == query.getMinScore() ? HYBRID_SEARCH_DEFAULT_MIN_SCORE : query.getMinScore());
    }

    @Override
    public Function<KnnSearch.Builder, ObjectBuilder<KnnSearch>> buildQuery(SearchQueryDTO query) {
        return null;
    }
}