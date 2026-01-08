package com.smart.vision.core.processor;

import cn.hutool.core.collection.CollectionUtil;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.smart.vision.core.model.context.QueryContext;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static com.smart.vision.core.constant.CommonConstant.DEFAULT_EMBEDDING_BOOST;
import static com.smart.vision.core.constant.CommonConstant.MINIMUM_SIMILARITY;

@Component
public class KnnQueryProcessor implements QueryProcessor{
    @Override
    public void process(QueryContext context, SearchRequest.Builder builder) {
//        invokeIfPresent(context::getQueryVector, CollectionUtil::isNotEmpty, v -> {
//            builder.knn(k -> k
//                    .field("imageEmbedding")
//                    .queryVector(v)
//                    .k(context.getTopK())
//                    .numCandidates(Math.max(100, context.getTopK() * 2))
//                    .boost(DEFAULT_EMBEDDING_BOOST)
//                    .similarity(null == context.getSimilarity() ? MINIMUM_SIMILARITY : context.getSimilarity())
//            );
//        });
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
