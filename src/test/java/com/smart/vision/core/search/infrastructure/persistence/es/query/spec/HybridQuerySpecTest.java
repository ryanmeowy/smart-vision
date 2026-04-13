package com.smart.vision.core.search.infrastructure.persistence.es.query.spec;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.smart.vision.core.common.model.GraphTriple;
import com.smart.vision.core.search.domain.model.HybridSearchParamDTO;
import com.smart.vision.core.search.infrastructure.persistence.es.query.GraphTriplesMatcher;
import com.smart.vision.core.search.infrastructure.persistence.es.query.HybridSearchKeywordMatcher;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class HybridQuerySpecTest {

    @Test
    void toSearchRequest_shouldContainKnnAndQuery_whenKeywordOrGraphExists() {
        HybridSearchKeywordMatcher keywordMatcher = Mockito.mock(HybridSearchKeywordMatcher.class);
        GraphTriplesMatcher graphMatcher = Mockito.mock(GraphTriplesMatcher.class);
        Query keywordQuery = Query.of(q -> q.match(m -> m.field("fileName").query("cat")));
        Query graphQuery = Query.of(q -> q.match(m -> m.field("relations.s").query("cat")));
        when(keywordMatcher.match(eq("cat"), anyBoolean())).thenReturn(Optional.of(keywordQuery));
        when(graphMatcher.match(anyList())).thenReturn(Optional.of(graphQuery));

        HybridSearchParamDTO param = HybridSearchParamDTO.builder()
                .queryVector(List.of(0.1f, 0.2f))
                .keyword("cat")
                .graphTriples(List.of(new GraphTriple("cat", "on", "sofa")))
                .limit(5)
                .topK(10)
                .enableOcr(true)
                .build();
        HybridQuerySpec spec = new HybridQuerySpec("idx", param, keywordMatcher, graphMatcher);

        SearchRequest request = spec.toSearchRequest();

        assertThat(request.index()).containsExactly("idx");
        assertThat(request.knn()).isNotNull();
        assertThat(request.knn()).hasSize(1);
        assertThat(request.query()).isNotNull();
        assertThat(request.highlight()).isNotNull();
        assertThat(request.highlight().fields().keySet()).contains("fileName", "tags", "ocrContent", "relations.s", "relations.p", "relations.o");
    }

    @Test
    void toSearchRequest_shouldContainOnlyKnn_whenNoKeywordAndNoGraph() {
        HybridSearchKeywordMatcher keywordMatcher = Mockito.mock(HybridSearchKeywordMatcher.class);
        GraphTriplesMatcher graphMatcher = Mockito.mock(GraphTriplesMatcher.class);
        when(graphMatcher.match(anyList())).thenReturn(Optional.empty());

        HybridSearchParamDTO param = HybridSearchParamDTO.builder()
                .queryVector(List.of(0.1f, 0.2f))
                .keyword(" ")
                .graphTriples(List.of())
                .limit(3)
                .topK(6)
                .build();
        HybridQuerySpec spec = new HybridQuerySpec("idx", param, keywordMatcher, graphMatcher);

        SearchRequest request = spec.toSearchRequest();

        assertThat(request.knn()).isNotNull();
        assertThat(request.knn()).hasSize(1);
        assertThat(request.query()).isNull();
        assertThat(request.highlight()).isNull();
    }
}
