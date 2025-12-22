package com.smart.vision.core.repository.query;

import co.elastic.clients.util.ObjectBuilder;
import com.smart.vision.core.model.dto.SearchQueryDTO;

import java.util.List;
import java.util.function.Function;

/**
 * Generic query component interface for building different types of Elasticsearch queries
 * 
 * @param <T> The type of ObjectBuilder to be used for query construction
 *
 * @author ryan
 * @since 2025/12/23
 */
public interface QueryComponent<T, R> {

    /**
     * Build a query component based on the provided search parameters
     * 
     * @param query The search query DTO containing search parameters
     * @return A function that builds the specific query component
     */
    Function<T, ObjectBuilder<R>> buildQuery(SearchQueryDTO query);


    Function<T, ObjectBuilder<R>> buildQuery(SearchQueryDTO query, List<Float> queryVector);
}