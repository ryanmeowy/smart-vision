package com.smart.vision.core.search.domain.strategy.impl;

import com.smart.vision.core.search.domain.model.ImageSearchResultDTO;
import com.smart.vision.core.search.domain.model.HybridSearchParamDTO;
import com.smart.vision.core.search.domain.port.QueryGraphParserPort;
import com.smart.vision.core.search.domain.port.SearchRerankPort;
import com.smart.vision.core.search.domain.port.SearchRerankPort.RerankItem;
import com.smart.vision.core.search.domain.ranking.RrfFusionService;
import com.smart.vision.core.search.infrastructure.persistence.es.document.ImageDocument;
import com.smart.vision.core.search.infrastructure.persistence.es.repository.ImageRepository;
import com.smart.vision.core.search.interfaces.rest.dto.SearchQueryDTO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class HybridRetrievalStrategyTest {

    @Mock
    private ImageRepository imageRepository;
    @Mock
    private QueryGraphParserPort queryGraphParserPort;
    @Mock
    private RrfFusionService rrfFusionService;
    @Mock
    private SearchRerankPort searchRerankPort;

    private HybridRetrievalStrategy strategy;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        strategy = new HybridRetrievalStrategy(
                imageRepository,
                queryGraphParserPort,
                rrfFusionService,
                searchRerankPort,
                meterRegistry
        );
        ReflectionTestUtils.setField(strategy, "rrfEnabled", false);
        ReflectionTestUtils.setField(strategy, "rerankEnabled", true);
        ReflectionTestUtils.setField(strategy, "rerankMaxDocChars", 1200);
        ReflectionTestUtils.setField(strategy, "rerankWindowEnabled", true);
        ReflectionTestUtils.setField(strategy, "rerankWindowSize", 40);
        ReflectionTestUtils.setField(strategy, "rerankWindowFactor", 3);
        ReflectionTestUtils.setField(strategy, "rerankWindowMin", 20);
        ReflectionTestUtils.setField(strategy, "rerankWindowMax", 80);
        ReflectionTestUtils.setField(strategy, "rerankFusionAlpha", 0.6d);
        ReflectionTestUtils.setField(strategy, "rerankFusionBeta", 0.4d);
    }

    @Test
    void search_shouldRerankOrderButKeepOriginalScores() {
        SearchQueryDTO query = new SearchQueryDTO();
        query.setKeyword("cat sofa");
        query.setTopK(10);
        query.setLimit(2);
        query.setEnableOcr(true);

        ImageDocument d1 = new ImageDocument();
        d1.setId(1L);
        d1.setFileName("cat-on-sofa.jpg");
        ImageSearchResultDTO first = ImageSearchResultDTO.builder()
                .document(d1)
                .rawScore(1.1d)
                .score(0.79d)
                .build();

        ImageDocument d2 = new ImageDocument();
        d2.setId(2L);
        d2.setFileName("living-room-cat.jpg");
        ImageSearchResultDTO second = ImageSearchResultDTO.builder()
                .document(d2)
                .rawScore(0.9d)
                .score(0.75d)
                .build();

        when(queryGraphParserPort.parseFromKeyword("cat sofa")).thenReturn(List.of());
        when(imageRepository.hybridSearch(any())).thenReturn(List.of(first, second));
        when(searchRerankPort.rerank(eq("cat sofa"), anyList(), eq(2)))
                .thenReturn(List.of(new RerankItem(1, 0.35d), new RerankItem(0, 0.20d)));

        List<ImageSearchResultDTO> results = strategy.search(query, List.of(0.1f, 0.2f));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getDocument().getId()).isEqualTo(2L);
        assertThat(results.get(0).getRawScore()).isEqualTo(0.9d);
        assertThat(results.get(0).getScore()).isEqualTo(0.75d);
        assertThat(results.get(1).getDocument().getId()).isEqualTo(1L);
        assertThat(results.get(1).getRawScore()).isEqualTo(1.1d);
        assertThat(results.get(1).getScore()).isEqualTo(0.79d);
        verify(searchRerankPort).rerank(eq("cat sofa"), anyList(), eq(2));
    }

    @Test
    void search_shouldOnlyRerankTopWindowAndKeepTailOrder() {
        ReflectionTestUtils.setField(strategy, "rerankWindowSize", 3);
        ReflectionTestUtils.setField(strategy, "rerankWindowMin", 1);
        ReflectionTestUtils.setField(strategy, "rerankWindowMax", 3);
        ReflectionTestUtils.setField(strategy, "rerankFusionAlpha", 0d);
        ReflectionTestUtils.setField(strategy, "rerankFusionBeta", 1d);

        SearchQueryDTO query = new SearchQueryDTO();
        query.setKeyword("cat");
        query.setTopK(10);
        query.setLimit(5);
        query.setEnableOcr(true);

        ImageSearchResultDTO d1 = hit(1L, 0.90d);
        ImageSearchResultDTO d2 = hit(2L, 0.80d);
        ImageSearchResultDTO d3 = hit(3L, 0.70d);
        ImageSearchResultDTO d4 = hit(4L, 0.60d);
        ImageSearchResultDTO d5 = hit(5L, 0.50d);

        when(queryGraphParserPort.parseFromKeyword("cat")).thenReturn(List.of());
        when(imageRepository.hybridSearch(any())).thenReturn(List.of(d1, d2, d3, d4, d5));
        when(searchRerankPort.rerank(eq("cat"), anyList(), eq(3)))
                .thenReturn(List.of(
                        new RerankItem(2, 0.95d),
                        new RerankItem(0, 0.90d),
                        new RerankItem(1, 0.80d)
                ));

        List<ImageSearchResultDTO> results = strategy.search(query, List.of(0.1f, 0.2f));

        assertThat(results).extracting(r -> r.getDocument().getId())
                .containsExactly(3L, 1L, 2L, 4L, 5L);
        verify(searchRerankPort).rerank(eq("cat"), anyList(), eq(3));
    }

    @Test
    void search_shouldFallbackToRetrievalOrderWhenRerankEmpty() {
        ReflectionTestUtils.setField(strategy, "rerankWindowSize", 3);
        ReflectionTestUtils.setField(strategy, "rerankWindowMin", 1);
        ReflectionTestUtils.setField(strategy, "rerankWindowMax", 3);

        SearchQueryDTO query = new SearchQueryDTO();
        query.setKeyword("cat");
        query.setTopK(10);
        query.setLimit(3);
        query.setEnableOcr(true);

        ImageSearchResultDTO d1 = hit(1L, 0.90d);
        ImageSearchResultDTO d2 = hit(2L, 0.80d);
        ImageSearchResultDTO d3 = hit(3L, 0.70d);

        when(queryGraphParserPort.parseFromKeyword("cat")).thenReturn(List.of());
        when(imageRepository.hybridSearch(any())).thenReturn(List.of(d1, d2, d3));
        when(searchRerankPort.rerank(eq("cat"), anyList(), eq(3))).thenReturn(List.of());

        List<ImageSearchResultDTO> results = strategy.search(query, List.of(0.1f, 0.2f));

        assertThat(results).extracting(r -> r.getDocument().getId())
                .containsExactly(1L, 2L, 3L);
        double fallbackCount = meterRegistry.get("smartvision.search.rerank.fallback").counter().count();
        assertThat(fallbackCount).isEqualTo(1d);
    }

    @Test
    void search_shouldUseDocIdAsTieBreakerWhenFusionScoreEqual() {
        ReflectionTestUtils.setField(strategy, "rerankWindowSize", 2);
        ReflectionTestUtils.setField(strategy, "rerankWindowMin", 1);
        ReflectionTestUtils.setField(strategy, "rerankWindowMax", 2);
        ReflectionTestUtils.setField(strategy, "rerankFusionAlpha", 0.5d);
        ReflectionTestUtils.setField(strategy, "rerankFusionBeta", 0.5d);

        SearchQueryDTO query = new SearchQueryDTO();
        query.setKeyword("cat");
        query.setTopK(10);
        query.setLimit(2);
        query.setEnableOcr(true);

        ImageSearchResultDTO d10 = hit(10L, 0.80d);
        ImageSearchResultDTO d2 = hit(2L, 0.80d);

        when(queryGraphParserPort.parseFromKeyword("cat")).thenReturn(List.of());
        when(imageRepository.hybridSearch(any())).thenReturn(List.of(d10, d2));
        when(searchRerankPort.rerank(eq("cat"), anyList(), eq(2)))
                .thenReturn(List.of(new RerankItem(0, 0.70d), new RerankItem(1, 0.70d)));

        List<ImageSearchResultDTO> results = strategy.search(query, List.of(0.1f, 0.2f));

        assertThat(results).extracting(r -> r.getDocument().getId())
                .containsExactly(2L, 10L);
    }

    @Test
    void search_shouldRespectRequestTopKAndFinalLimitInHybridFlow() {
        ReflectionTestUtils.setField(strategy, "rrfEnabled", true);
        ReflectionTestUtils.setField(strategy, "rerankEnabled", false);
        ReflectionTestUtils.setField(strategy, "rrfCandidateMultiplier", 4);
        ReflectionTestUtils.setField(strategy, "rrfMaxCandidates", 200);
        ReflectionTestUtils.setField(strategy, "rrfRankConstant", 60);

        SearchQueryDTO query = new SearchQueryDTO();
        query.setKeyword("cat");
        query.setTopK(5);
        query.setLimit(3);
        query.setEnableOcr(true);

        List<ImageSearchResultDTO> hybridHits = List.of(
                hit(1L, 0.99d),
                hit(2L, 0.98d),
                hit(3L, 0.97d),
                hit(4L, 0.96d),
                hit(5L, 0.95d),
                hit(6L, 0.94d)
        );
        when(queryGraphParserPort.parseFromKeyword("cat")).thenReturn(List.of());
        when(imageRepository.hybridSearch(any())).thenReturn(hybridHits);
        when(imageRepository.vectorSearch(anyList(), eq(12))).thenReturn(hybridHits);
        when(imageRepository.textSearch(eq("cat"), eq(12), eq(true))).thenReturn(hybridHits);
        when(rrfFusionService.fuse(anyList(), eq(12), eq(60))).thenReturn(hybridHits);

        List<ImageSearchResultDTO> results = strategy.search(query, List.of(0.1f, 0.2f));

        ArgumentCaptor<HybridSearchParamDTO> hybridCaptor = ArgumentCaptor.forClass(HybridSearchParamDTO.class);
        verify(imageRepository).hybridSearch(hybridCaptor.capture());
        assertThat(hybridCaptor.getValue().getTopK()).isEqualTo(5);
        assertThat(hybridCaptor.getValue().getLimit()).isEqualTo(12);
        verify(imageRepository, times(1)).vectorSearch(anyList(), eq(12));
        verify(imageRepository, times(1)).textSearch(eq("cat"), eq(12), eq(true));
        assertThat(results).hasSize(3);
    }

    @Test
    void search_shouldRouteToNativeRrfWhenEnabled() {
        ReflectionTestUtils.setField(strategy, "rrfEnabled", true);
        ReflectionTestUtils.setField(strategy, "rrfNativeEnabled", true);
        ReflectionTestUtils.setField(strategy, "rerankEnabled", false);
        ReflectionTestUtils.setField(strategy, "rrfCandidateMultiplier", 4);
        ReflectionTestUtils.setField(strategy, "rrfMaxCandidates", 200);
        ReflectionTestUtils.setField(strategy, "rrfRankConstant", 60);

        SearchQueryDTO query = new SearchQueryDTO();
        query.setKeyword("cat");
        query.setTopK(5);
        query.setLimit(3);
        query.setEnableOcr(true);

        List<ImageSearchResultDTO> nativeHits = List.of(
                hit(1L, 0.99d),
                hit(2L, 0.98d),
                hit(3L, 0.97d),
                hit(4L, 0.96d)
        );
        List<ImageSearchResultDTO> vectorHits = List.of(
                hit(1L, 0.90d),
                hit(3L, 0.89d)
        );
        List<ImageSearchResultDTO> textHits = List.of(
                hit(2L, 0.88d),
                hit(3L, 0.87d)
        );
        when(queryGraphParserPort.parseFromKeyword("cat")).thenReturn(List.of());
        when(imageRepository.hybridSearchNativeRrf(any(), eq(60), eq(12))).thenReturn(nativeHits);
        when(imageRepository.vectorSearch(anyList(), eq(12))).thenReturn(vectorHits);
        when(imageRepository.textSearch(eq("cat"), eq(12), eq(true))).thenReturn(textHits);

        List<ImageSearchResultDTO> results = strategy.search(query, List.of(0.1f, 0.2f));

        ArgumentCaptor<HybridSearchParamDTO> nativeCaptor = ArgumentCaptor.forClass(HybridSearchParamDTO.class);
        verify(imageRepository).hybridSearchNativeRrf(nativeCaptor.capture(), eq(60), eq(12));
        assertThat(nativeCaptor.getValue().getTopK()).isEqualTo(5);
        assertThat(nativeCaptor.getValue().getLimit()).isEqualTo(12);
        verify(imageRepository, never()).hybridSearch(any());
        verify(imageRepository, times(1)).vectorSearch(anyList(), eq(12));
        verify(imageRepository, times(1)).textSearch(eq("cat"), eq(12), eq(true));
        verify(rrfFusionService, never()).fuse(anyList(), eq(12), eq(60));
        assertThat(results).hasSize(3);
        assertThat(results.get(0).getDocument().getId()).isEqualTo(1L);
        assertThat(results.get(0).getVectorRecallHit()).isTrue();
        assertThat(results.get(0).getTextRecallHit()).isFalse();
        assertThat(results.get(1).getDocument().getId()).isEqualTo(2L);
        assertThat(results.get(1).getVectorRecallHit()).isFalse();
        assertThat(results.get(1).getTextRecallHit()).isTrue();
        assertThat(results.get(2).getDocument().getId()).isEqualTo(3L);
        assertThat(results.get(2).getVectorRecallHit()).isTrue();
        assertThat(results.get(2).getTextRecallHit()).isTrue();
    }

    private ImageSearchResultDTO hit(long id, double score) {
        ImageDocument doc = new ImageDocument();
        doc.setId(id);
        doc.setFileName("f-" + id + ".jpg");
        return ImageSearchResultDTO.builder()
                .document(doc)
                .rawScore(score)
                .score(score)
                .build();
    }
}
