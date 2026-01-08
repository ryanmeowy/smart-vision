package com.smart.vision.core.processor;

import cn.hutool.core.collection.CollectionUtil;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.smart.vision.core.model.context.QueryContext;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class KnnQueryProcessor implements QueryProcessor{
    @Override
    public void process(QueryContext context, SearchRequest.Builder builder) {
        invokeIfPresent(context::getKnnQuery, Objects::nonNull, kq -> {
            KnnSearch.Builder knnBuilder = new KnnSearch.Builder();
            knnBuilder.field(kq.getFieldName());
            knnBuilder.queryVector(kq.getQueryVector());
            builder.knn(knnBuilder.build());

        });



        invokeIfPresent(context::getQueryVector, CollectionUtil::isNotEmpty, v -> {
            KnnSearch.Builder knnBuilder = new KnnSearch.Builder();
            knnBuilder.field("imageEmbedding");
            knnBuilder.queryVector(v);
            invokeIfPresent(context::getKnnQuery, Objects::nonNull, kq -> {
                invokeIfPresent(kq::getTopK, Objects::nonNull, knnBuilder::k);
                invokeIfPresent(kq::getNumCandidates, Objects::nonNull, knnBuilder::numCandidates);
                invokeIfPresent(kq::getBoost, Objects::nonNull, knnBuilder::boost);
                invokeIfPresent(kq::getSimilarity, Objects::nonNull, knnBuilder::similarity);
            });
            builder.knn(knnBuilder.build());
        });
    }

}
