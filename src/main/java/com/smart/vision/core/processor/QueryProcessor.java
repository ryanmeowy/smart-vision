package com.smart.vision.core.processor;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.smart.vision.core.model.context.QueryContext;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public interface QueryProcessor {

    void process(QueryContext context, SearchRequest.Builder builder);

    default <T> void invokeIfPresent(Supplier<T> s, Predicate<T> p, Consumer<T> c) {
        Optional.ofNullable(s.get()).filter(p).ifPresent(c);
    }
}
