package com.smart.vision.core.model.context;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.smart.vision.core.model.dto.GraphTripleDTO;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.function.Function;

@Data
@Builder
public class QueryContext {
    private String id;
    private String keyword;
    private String indexName;
    private Integer limit;
    private List<Object> searchAfter;
    private List<SortOptions> sortOptions;
    private KnnQuery knnQuery;
    private List<Function<String, Query>> keywordFunc;
    private List<Function<String, Query>> filter;
    private List<GraphTripleDTO> graphTriples;

    @Data
    @Builder
    public static class KnnQuery {
        private String fieldName;
        private List<Float> queryVector;
        private Integer topK;
        private Float similarity;
        private Integer numCandidates;
        private Float boost;
        private List<Function<String, Query>> filter;
    }
}
