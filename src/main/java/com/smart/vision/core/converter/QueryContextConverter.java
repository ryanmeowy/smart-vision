package com.smart.vision.core.converter;

import co.elastic.clients.elasticsearch._types.SortOptions;
import com.smart.vision.core.model.context.QueryContext;
import com.smart.vision.core.model.context.SortContext;
import com.smart.vision.core.model.dto.SearchQueryDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.smart.vision.core.constant.CommonConstant.DEFAULT_EMBEDDING_BOOST;
import static com.smart.vision.core.constant.CommonConstant.DEFAULT_NUM_CANDIDATES;
import static com.smart.vision.core.constant.CommonConstant.SMART_GALLERY_V1;

@Component
public class QueryContextConverter {
    public QueryContext context4HybridSearch(SearchQueryDTO query, List<Float> queryVector, List<SortContext> sortContextList) {
        return QueryContext.builder()
                .indexName(SMART_GALLERY_V1)
                .limit(query.getLimit())
                .searchAfter(query.getSearchAfter())
                .sortOptions(convert2SortOptions(sortContextList))
                .knnQuery(knnQuery4HybridSearch(query, queryVector))
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
                .numCandidates(Math.max(DEFAULT_NUM_CANDIDATES, query.getTopK() * 2))
                .build();
    }

    public QueryContext context4SimilarSearch(List<Float> vector, Integer topK, String excludeDocId, List<SortContext> sortContextList) {
        return QueryContext.builder()
                .indexName(SMART_GALLERY_V1)
                .limit(topK)
                .sortOptions(convert2SortOptions(sortContextList))

    }
}
