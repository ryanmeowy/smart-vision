package com.smart.vision.core.builder;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import com.google.common.collect.Lists;
import com.smart.vision.core.config.VectorConfig;
import com.smart.vision.core.model.context.QueryContext;
import com.smart.vision.core.model.context.SortContext;
import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.query.HybridSearchKeywordMatcher;
import com.smart.vision.core.query.SimilarSearchIdMatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.smart.vision.core.constant.CommonConstant.DEFAULT_EMBEDDING_BOOST;
import static com.smart.vision.core.constant.CommonConstant.DEFAULT_NUM_CANDIDATES;
import static com.smart.vision.core.constant.CommonConstant.NUM_CANDIDATES_FACTOR;
import static com.smart.vision.core.constant.CommonConstant.SIMILAR_QUERIES_SIMILARITY;

@Component
@RequiredArgsConstructor
public class QueryContextBuilder {

    private final HybridSearchKeywordMatcher hybridSearchKeywordMatcher;
    private final SimilarSearchIdMatcher similarSearchIdMatcher;
    private final VectorConfig vectorConfig;

    public QueryContext context4HybridSearch(SearchQueryDTO query, List<Float> queryVector, List<SortContext> sortContextList) {
        return QueryContext.builder()
                .indexName(vectorConfig.getIndexName())
                .keyword(query.getKeyword())
                .limit(query.getLimit())
                .searchAfter(query.getSearchAfter())
                .sortOptions(convert2SortOptions(null == sortContextList ? defaultSort() : sortContextList))
                .knnQuery(knnQuery4HybridSearch(query, queryVector))
                .keywordFunc(Lists.newArrayList(hybridSearchKeywordMatcher::match))
                .build();
    }

    private List<SortOptions> convert2SortOptions(List<SortContext> sortContextList) {
        if (CollectionUtils.isEmpty(sortContextList)) {
            return Collections.emptyList();
        }
        sortContextList = sortContextList.stream().filter(Objects::nonNull)
                .filter(x -> null != x.getKind())
                .filter(x -> null != x.getOrder())
                .filter(x -> x.getKind() != SortOptions.Kind.Score && null == x.getField())
                .toList();

        List<SortOptions> sortOptionsList = new ArrayList<>();
        for (SortContext sortContext : sortContextList) {
            if (SortOptions.Kind.Score == sortContext.getKind()) {
                sortOptionsList.add(new SortOptions.Builder().score(sc -> sc.order(sortContext.getOrder())).build());
            }
            if (SortOptions.Kind.Field == sortContext.getKind()) {
                sortOptionsList.add(new SortOptions.Builder().field(f -> f.field(sortContext.getField()).order(sortContext.getOrder())).build());
            }
            // TODO other kinds
        }
        return sortOptionsList;
    }

    private QueryContext.KnnQuery knnQuery4HybridSearch(SearchQueryDTO query, List<Float> queryVector) {
        return QueryContext.KnnQuery.builder()
                .fieldName("imageEmbedding")
                .queryVector(queryVector)
                .topK(query.getTopK())
                .similarity(query.getSimilarity())
                .boost(DEFAULT_EMBEDDING_BOOST)
                .numCandidates(Math.max(DEFAULT_NUM_CANDIDATES, query.getTopK() * NUM_CANDIDATES_FACTOR))
                .build();
    }

    public QueryContext context4SimilarSearch(List<Float> queryVector, Integer topK, String excludeDocId, List<SortContext> sortContextList) {
        return QueryContext.builder()
                .indexName(vectorConfig.getIndexName())
                .id(excludeDocId)
                .limit(topK)
                .sortOptions(convert2SortOptions(null == sortContextList ? defaultSort() : sortContextList))
                .knnQuery(knnQuery4SimilarSearch(queryVector, topK))
                .filter(Lists.newArrayList(similarSearchIdMatcher::match))
                .build();

    }

    private QueryContext.KnnQuery knnQuery4SimilarSearch(List<Float> queryVector, Integer topK) {
        return QueryContext.KnnQuery.builder()
                .fieldName("imageEmbedding")
                .queryVector(queryVector)
                .filter(Lists.newArrayList(similarSearchIdMatcher::match))
                .topK(topK)
                .numCandidates(Math.max(NUM_CANDIDATES_FACTOR * topK, DEFAULT_NUM_CANDIDATES))
                .similarity(SIMILAR_QUERIES_SIMILARITY)
                .build();
    }

    public QueryContext context4DuplicateSearch(List<Float> vector, double threshold) {
        return QueryContext.builder()
                .indexName(vectorConfig.getIndexName())
                .limit(1)
                .knnQuery(knnQuery4DuplicateSearch(vector, threshold))
                .build();
    }

    private QueryContext.KnnQuery knnQuery4DuplicateSearch(List<Float> vector, double threshold) {
        return QueryContext.KnnQuery.builder()
                .fieldName("imageEmbedding")
                .queryVector(vector)
                .topK(1)
                .numCandidates(DEFAULT_NUM_CANDIDATES)
                .similarity((float) threshold)
                .build();
    }

    private List<SortContext> defaultSort() {
        SortContext scoreSort = SortContext.builder().kind(SortOptions.Kind.Score).order(SortOrder.Desc).build();
        SortContext idSort = SortContext.builder().kind(SortOptions.Kind.Field).field("id").order(SortOrder.Asc).build();
        return Lists.newArrayList(scoreSort, idSort);
    }
}
