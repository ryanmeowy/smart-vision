package com.smart.vision.core.query;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.springframework.stereotype.Component;

@Component
public class SimilarSearchIdMatcher implements FieldMatcher {

    @Override
    public Query match(String id) {
        return Query.of(f -> f.bool(b -> b
                .mustNot(mn -> mn.term(t -> t.field("id").value(Long.parseLong(id))))));
    }
}
