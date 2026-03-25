package com.smart.vision.core.query.spec;

import cn.hutool.core.collection.CollectionUtil;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.smart.vision.core.constant.EmbeddingConstant;
import com.smart.vision.core.config.SimilarityConfig;
import com.smart.vision.core.model.dto.GraphTripleDTO;
import com.smart.vision.core.query.SimilarSearchIdMatcher;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Similar search spec: KNN + exclude self (by id filter).
 */
public class SimilarQuerySpec implements QuerySpec {

    private final String indexName;
    private final List<Float> vector;
    private final Integer topK;
    private final String excludeDocId;
    private final SimilarSearchIdMatcher idMatcher;
    private final SimilarityConfig similarityConfig;

    public SimilarQuerySpec(String indexName,
                              List<Float> vector,
                              Integer topK,
                              String excludeDocId,
                              SimilarSearchIdMatcher idMatcher,
                              SimilarityConfig similarityConfig) {
        this.indexName = indexName;
        this.vector = vector;
        this.topK = topK;
        this.excludeDocId = excludeDocId;
        this.idMatcher = idMatcher;
        this.similarityConfig = similarityConfig;
    }

    @Override
    public SearchRequest toSearchRequest() {
        SearchRequest.Builder builder = new SearchRequest.Builder();
        builder.index(indexName);
        builder.size(topK);
        builder.sort(defaultSort());

        // root query: mustNot self (consistent with existing QueryContextConvertor+processors flow)
        Query filterQuery = idMatcher.match(excludeDocId)
                .orElseThrow(() -> new IllegalArgumentException("excludeDocId cannot be empty"));
        builder.query(filterQuery);

        // knn query: same filter to ensure exclude is applied at KNN stage too
        KnnSearch knn = new KnnSearch.Builder()
                .field("imageEmbedding")
                .queryVector(vector)
                .k(topK)
                .numCandidates(Math.max(EmbeddingConstant.NUM_CANDIDATES_FACTOR * topK, EmbeddingConstant.DEFAULT_NUM_CANDIDATES))
                .similarity(similarityConfig.forSimilarSearch())
                .filter(filterQuery)
                .build();
        builder.knn(knn);

        return builder.build();
    }

    private static List<SortOptions> defaultSort() {
        return List.of(
                new SortOptions.Builder().score(sc -> sc.order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)).build(),
                new SortOptions.Builder().field(f -> f.field("id").order(co.elastic.clients.elasticsearch._types.SortOrder.Asc)).build()
        );
    }
}

