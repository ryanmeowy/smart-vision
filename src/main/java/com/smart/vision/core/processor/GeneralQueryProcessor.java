package com.smart.vision.core.processor;

import cn.hutool.core.collection.CollectionUtil;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.smart.vision.core.model.context.QueryContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class GeneralQueryProcessor implements QueryProcessor {
    @Override
    public void process(QueryContext context, SearchRequest.Builder builder) {
        invokeIfPresent(context::getIndexName, StringUtils::hasText, builder::index);
        invokeIfPresent(context::getLimit, Objects::nonNull, builder::size);
        invokeIfPresent(context::getSearchAfter, CollectionUtil::isNotEmpty,
                objects -> builder.searchAfter(context.getSearchAfter().stream().map(FieldValue::of).collect(Collectors.toList())));
        invokeIfPresent(context::getSortOptions, CollectionUtil::isNotEmpty, builder::sort);
        invokeIfPresent(context::getId, StringUtils::hasText,
                id -> invokeIfPresent(context::getFilter, CollectionUtil::isNotEmpty,
                        f -> f.forEach(x -> builder.query(x.apply(id)))));
    }
}
