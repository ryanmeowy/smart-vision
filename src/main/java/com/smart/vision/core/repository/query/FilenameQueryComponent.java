package com.smart.vision.core.repository.query;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.util.ObjectBuilder;
import com.smart.vision.core.model.dto.SearchQueryDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * Query component for building filename search queries
 *
 * @author ryan
 * @since 2025/12/23
 */
@Component
public class FilenameQueryComponent implements QueryComponent<Query.Builder,  Query> {

    @Override
    public Function<Query.Builder, ObjectBuilder<Query>> buildQuery(SearchQueryDTO query) {
        return q -> q
                .match(m -> m
                        .field("filename")
                        .query(query.getKeyword())
                        .boost(0.2f)
                );
    }

    @Override
    public Function<Query.Builder, ObjectBuilder<Query>> buildQuery(SearchQueryDTO query, List<Float> queryVector) {
        return null;
    }
}