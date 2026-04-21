package com.smart.vision.core.search.infrastructure.persistence.es.query.factory;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.smart.vision.core.common.config.VectorConfig;
import com.smart.vision.core.common.model.GraphTriple;
import com.smart.vision.core.search.config.AppSearchProperties;
import com.smart.vision.core.search.domain.model.HybridSearchParamDTO;
import com.smart.vision.core.search.infrastructure.persistence.es.query.GraphTriplesMatcher;
import com.smart.vision.core.search.infrastructure.persistence.es.query.HybridSearchKeywordMatcher;
import com.smart.vision.core.search.infrastructure.persistence.es.query.SimilarSearchIdMatcher;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class SearchRequestFactoryTest {

    @Test
    void buildTextOnly_shouldUseReadAliasAsTargetIndex() {
        VectorConfig vectorConfig = new VectorConfig();
        vectorConfig.setIndexName("physical-index");
        vectorConfig.setReadAlias("read-alias");

        HybridSearchKeywordMatcher keywordMatcher = Mockito.mock(HybridSearchKeywordMatcher.class);
        when(keywordMatcher.match(eq("cat"), anyBoolean()))
                .thenReturn(Optional.of(Query.of(q -> q.match(m -> m.field("fileName").query("cat")))));
        SimilarSearchIdMatcher similarSearchIdMatcher = Mockito.mock(SimilarSearchIdMatcher.class);
        GraphTriplesMatcher graphTriplesMatcher = Mockito.mock(GraphTriplesMatcher.class);

        SearchRequestFactory factory = new SearchRequestFactory(
                vectorConfig,
                keywordMatcher,
                similarSearchIdMatcher,
                graphTriplesMatcher,
                new AppSearchProperties()
        );

        SearchRequest request = factory.buildTextOnly("cat", 5, true);

        assertThat(request.index()).containsExactly("read-alias");
        assertThat(request.size()).isEqualTo(5);
        assertThat(request.query()).isNotNull();
    }

    @Test
    void buildHybrid_shouldBuildOnReadAlias_withKnnAndMergedQuery() {
        VectorConfig vectorConfig = new VectorConfig();
        vectorConfig.setIndexName("physical-index");
        vectorConfig.setReadAlias("read-alias");

        HybridSearchKeywordMatcher keywordMatcher = Mockito.mock(HybridSearchKeywordMatcher.class);
        GraphTriplesMatcher graphTriplesMatcher = Mockito.mock(GraphTriplesMatcher.class);
        SimilarSearchIdMatcher similarSearchIdMatcher = Mockito.mock(SimilarSearchIdMatcher.class);
        Query keywordQuery = Query.of(q -> q.match(m -> m.field("fileName").query("cat")));
        Query graphQuery = Query.of(q -> q.match(m -> m.field("relations.s").query("cat")));
        when(keywordMatcher.match(eq("cat"), anyBoolean())).thenReturn(Optional.of(keywordQuery));
        when(graphTriplesMatcher.match(anyList())).thenReturn(Optional.of(graphQuery));

        SearchRequestFactory factory = new SearchRequestFactory(
                vectorConfig,
                keywordMatcher,
                similarSearchIdMatcher,
                graphTriplesMatcher,
                new AppSearchProperties()
        );

        HybridSearchParamDTO param = HybridSearchParamDTO.builder()
                .queryVector(List.of(0.1f, 0.2f))
                .graphTriples(List.of(new GraphTriple("cat", "on", "sofa")))
                .keyword("cat")
                .topK(7)
                .limit(3)
                .build();

        SearchRequest request = factory.buildHybrid(param);

        assertThat(request.index()).containsExactly("read-alias");
        assertThat(request.knn()).isNotNull();
        assertThat(request.knn()).hasSize(1);
        assertThat(request.query()).isNotNull();
    }
}
