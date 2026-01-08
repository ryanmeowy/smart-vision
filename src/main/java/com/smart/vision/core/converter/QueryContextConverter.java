package com.smart.vision.core.converter;

import co.elastic.clients.elasticsearch._types.SortOptions;
import com.smart.vision.core.model.context.QueryContext;
import com.smart.vision.core.model.context.SortContext;
import com.smart.vision.core.model.dto.SearchQueryDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.smart.vision.core.constant.CommonConstant.DEFAULT_EMBEDDING_BOOST;
import static com.smart.vision.core.constant.CommonConstant.DEFAULT_NUM_CANDIDATES;
import static com.smart.vision.core.constant.CommonConstant.SMART_GALLERY_V1;

@Component
public class QueryContextConverter {
    public QueryContext context4HybridSearch(SearchQueryDTO query, List<Float> queryVector, SortContext... sortContext) {
        return QueryContext.builder()
                .keyword(query.getKeyword())
                .queryVector(queryVector)
                .indexName(SMART_GALLERY_V1)
                .limit(query.getLimit())
                .searchAfter(query.getSearchAfter())
                .sortOptions(convert2SortOptions(sortContext))
                .knnQuery(convert2KnnQuery(query))
                .fieldQuery(convert2FieldQuery(query))
                .build();
    }

    private List<QueryContext.FieldQuery> convert2FieldQuery(SearchQueryDTO query) {
        return null;
    }


    private List<SortOptions> convert2SortOptions(SortContext... sortContextList) {
        if (sortContextList == null || sortContextList.length == 0) {
            return Collections.emptyList();
        }

        List<SortContext> sortContexts = Arrays.stream(sortContextList).filter(Objects::nonNull)
                .filter(x -> null != x.getKind())
                .filter(x -> null != x.getOrder())
                .filter(x -> x.getKind() != SortOptions.Kind.Score && null == x.getField())
                .toList();

        List<SortOptions> sortOptionsList = new ArrayList<>();
        for (SortContext sortContext : sortContexts) {
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

    private QueryContext.KnnQuery convert2KnnQuery(SearchQueryDTO query) {
        return QueryContext.KnnQuery.builder()
                .topK(query.getTopK())
                .similarity(query.getSimilarity())
                .boost(DEFAULT_EMBEDDING_BOOST)
                .numCandidates(Math.max(DEFAULT_NUM_CANDIDATES, query.getTopK() * 2))
                .build();
    }
}
