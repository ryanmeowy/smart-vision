package com.smart.vision.core.query;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;

public interface FieldMatcher {

    Query match(String param);

}