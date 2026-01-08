package com.smart.vision.core.model.context;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.util.ObjectBuilder;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class QueryContext {
    private String keyword;
    private List<Float> queryVector;
    private String indexName;
    private Integer limit;
    private List<Object> searchAfter;
    private List<SortOptions> sortOptions;
    private KnnQuery knnQuery;
    private List<FieldQuery> fieldQuery;

    @Data
    @Builder
    public static class KnnQuery {
        private String fieldName;
        private List<Float> queryVector;
        private Integer topK;
        private Float similarity;
        private Integer numCandidates;
        private Float boost;
    }

    @Data
    @Builder
    public static class FieldQuery<T> {
        private String field;
        private Object value;
        private Query.Kind kind;
        private ObjectBuilder<T> objectBuilder;
    }
}
