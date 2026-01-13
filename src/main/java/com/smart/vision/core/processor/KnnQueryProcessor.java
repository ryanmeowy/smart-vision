package com.smart.vision.core.processor;

import cn.hutool.core.collection.CollectionUtil;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.smart.vision.core.model.context.QueryContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Component
public class KnnQueryProcessor implements QueryProcessor {
    @Override
    public void process(QueryContext context, SearchRequest.Builder builder) {
        invokeIfPresent(context::getKnnQuery, Objects::nonNull, kq -> {
            KnnSearch.Builder knnBuilder = new KnnSearch.Builder();
            knnBuilder.field(kq.getFieldName());
            knnBuilder.queryVector(kq.getQueryVector());
            invokeIfPresent(kq::getTopK, Objects::nonNull, knnBuilder::k);
            invokeIfPresent(kq::getNumCandidates, Objects::nonNull, knnBuilder::numCandidates);
            invokeIfPresent(kq::getBoost, Objects::nonNull, knnBuilder::boost);
            invokeIfPresent(kq::getSimilarity, Objects::nonNull, knnBuilder::similarity);
            invokeIfPresent(context::getId, StringUtils::hasText,
                    id -> invokeIfPresent(kq::getFilter, CollectionUtil::isNotEmpty,
                            f -> f.forEach(x -> knnBuilder.filter(x.apply(id)))));
            builder.knn(knnBuilder.build());
        });
    }
}
