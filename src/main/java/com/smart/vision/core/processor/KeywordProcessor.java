package com.smart.vision.core.processor;

import cn.hutool.core.collection.CollectionUtil;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.smart.vision.core.model.context.QueryContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class KeywordProcessor implements QueryProcessor {

    @Override
    public void process(QueryContext context, SearchRequest.Builder builder) {
        invokeIfPresent(context::getKeyword, Objects::nonNull,
                k -> invokeIfPresent(context::getKeywordFunc, CollectionUtil::isNotEmpty,
                        f -> f.forEach(y -> builder.query(y.apply(k)))));
    }
}