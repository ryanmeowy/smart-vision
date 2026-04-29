package com.smart.vision.core.search.application.impl;

import com.smart.vision.core.search.application.KbQueryEmbeddingService;
import com.smart.vision.core.search.config.AppSearchProperties;
import com.smart.vision.core.search.domain.model.KbSegmentHit;
import com.smart.vision.core.search.domain.port.KbSegmentSearchPort;
import com.smart.vision.core.search.infrastructure.persistence.es.document.KbSegmentDocument;
import com.smart.vision.core.search.interfaces.rest.dto.KbSearchQueryDTO;
import com.smart.vision.core.search.interfaces.rest.dto.KbSearchResultDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnifiedSearchServiceImplTest {

    @Mock
    private KbSegmentSearchPort kbSegmentSearchPort;
    @Mock
    private KbQueryEmbeddingService kbQueryEmbeddingService;

    @Test
    void search_shouldMergeTextAndVectorHitsWithUnifiedSchema() {
        UnifiedSearchServiceImpl service = buildService();

        KbSearchQueryDTO query = new KbSearchQueryDTO();
        query.setQuery("mysql");
        query.setTopK(5);
        query.setLimit(3);
        query.setStrategy("KB_RRF");
        query.setEnableRerank(Boolean.FALSE);

        when(kbQueryEmbeddingService.embedQuery("mysql")).thenReturn(List.of(0.1f, 0.2f));
        when(kbSegmentSearchPort.textSearch("mysql", 5)).thenReturn(List.of(
                KbSegmentHit.builder()
                        .document(buildDoc("seg-1", "TEXT", "TEXT_CHUNK", "mysql notes", "mysql chunk", null, 2))
                        .rawScore(3.2d)
                        .highlights(Map.of("contentText", "<em>mysql</em> chunk"))
                        .highlightFields(List.of("contentText"))
                        .build()
        ));
        when(kbSegmentSearchPort.vectorSearch(List.of(0.1f, 0.2f), 5)).thenReturn(List.of(
                KbSegmentHit.builder()
                        .document(buildDoc("seg-1", "TEXT", "TEXT_CHUNK", "mysql notes", "mysql chunk", null, 2))
                        .rawScore(1.1d)
                        .highlights(Map.of())
                        .highlightFields(List.of())
                        .build(),
                KbSegmentHit.builder()
                        .document(buildDoc("seg-2", "IMAGE", "IMAGE_CAPTION", "diagram", "mysql architecture", null, null))
                        .rawScore(0.9d)
                        .highlights(Map.of())
                        .highlightFields(List.of())
                        .build()
        ));

        List<KbSearchResultDTO> results = service.search(query);

        assertThat(results).hasSize(2);
        assertThat(results.getFirst().getSegmentId()).isEqualTo("seg-1");
        assertThat(results.getFirst().getSegmentType()).isEqualTo("TEXT_CHUNK");
        assertThat(results.getFirst().getResultType()).isEqualTo("TEXT_CHUNK");
        assertThat(results.getFirst().getContent()).contains("mysql");
        assertThat(results.getFirst().getSnippet()).contains("mysql");
        assertThat(results.getFirst().getAnchor()).isNotNull();
        assertThat(results.getFirst().getAnchor().getPageNo()).isEqualTo(2);
        assertThat(results.getFirst().getExplain().getMatchedBy().isVector()).isTrue();
        assertThat(results.getFirst().getExplain().getMatchedBy().isContent()).isTrue();
        assertThat(results.getFirst().getExplain().getStrategyEffective()).isEqualTo("KB_RRF");
        assertThat(results.getFirst().getExplain().getTextSignals()).isNotNull();
        assertThat(results.getFirst().getExplain().getTextSignals().isSemantic()).isTrue();
        assertThat(results.getFirst().getExplain().getTextSignals().isKeyword()).isTrue();
        assertThat(results.getFirst().getExplain().getImageSignals()).isNull();
        assertThat(results.get(1).getAssetType()).isEqualTo("IMAGE");
        assertThat(results.get(1).getExplain().getImageSignals()).isNotNull();
        assertThat(results.get(1).getExplain().getImageSignals().isVector()).isTrue();
        assertThat(results.get(1).getExplain().getImageSignals().isCaption()).isTrue();
        assertThat(results.get(1).getExplain().getImageSignals().isTag()).isTrue();
        assertThat(results.get(1).getExplain().getHitSources()).contains("TAG");
        assertThat(results.get(1).getExplain().getTextSignals()).isNull();
    }

    @Test
    void search_shouldBuildTextOnlyExplainSignals() {
        UnifiedSearchServiceImpl service = buildService();

        KbSearchQueryDTO query = new KbSearchQueryDTO();
        query.setQuery("mysql");
        query.setTopK(5);
        query.setLimit(3);

        when(kbQueryEmbeddingService.embedQuery("mysql")).thenReturn(List.of(0.1f, 0.2f));
        when(kbSegmentSearchPort.textSearch("mysql", 5)).thenReturn(List.of(
                KbSegmentHit.builder()
                        .document(buildDoc("seg-t1", "TEXT", "TEXT_CHUNK", "mysql notes", "mysql chunk", null, 3))
                        .rawScore(2.1d)
                        .highlights(Map.of("contentText", "<em>mysql</em> chunk"))
                        .highlightFields(List.of("contentText"))
                        .build()
        ));
        when(kbSegmentSearchPort.vectorSearch(List.of(0.1f, 0.2f), 5)).thenReturn(List.of());

        List<KbSearchResultDTO> results = service.search(query);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getSegmentType()).isEqualTo("TEXT_CHUNK");
        assertThat(results.getFirst().getExplain().getTextSignals()).isNotNull();
        assertThat(results.getFirst().getExplain().getTextSignals().isSemantic()).isFalse();
        assertThat(results.getFirst().getExplain().getTextSignals().isKeyword()).isTrue();
        assertThat(results.getFirst().getExplain().getTextSignals().isPageHit()).isTrue();
        assertThat(results.getFirst().getExplain().getTextSignals().isChunkHit()).isTrue();
        assertThat(results.getFirst().getExplain().getImageSignals()).isNull();
    }

    @Test
    void search_shouldBuildImageOnlyExplainSignals() {
        UnifiedSearchServiceImpl service = buildService();

        KbSearchQueryDTO query = new KbSearchQueryDTO();
        query.setQuery("mysql");
        query.setTopK(5);
        query.setLimit(3);

        when(kbQueryEmbeddingService.embedQuery("mysql")).thenReturn(List.of(0.1f, 0.2f));
        when(kbSegmentSearchPort.textSearch("mysql", 5)).thenReturn(List.of());
        when(kbSegmentSearchPort.vectorSearch(List.of(0.1f, 0.2f), 5)).thenReturn(List.of(
                KbSegmentHit.builder()
                        .document(buildDoc("seg-i1", "IMAGE", "IMAGE_OCR_BLOCK", "diagram", null, "mysql ocr text", null))
                        .rawScore(1.8d)
                        .highlights(Map.of())
                        .highlightFields(List.of())
                        .build()
        ));

        List<KbSearchResultDTO> results = service.search(query);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getSegmentType()).isEqualTo("IMAGE_OCR_BLOCK");
        assertThat(results.getFirst().getExplain().getTextSignals()).isNull();
        assertThat(results.getFirst().getExplain().getImageSignals()).isNotNull();
        assertThat(results.getFirst().getExplain().getImageSignals().isVector()).isTrue();
        assertThat(results.getFirst().getExplain().getImageSignals().isOcr()).isTrue();
        assertThat(results.getFirst().getExplain().getImageSignals().isCaption()).isFalse();
        assertThat(results.getFirst().getExplain().getImageSignals().isTag()).isTrue();
        assertThat(results.getFirst().getExplain().getHitSources()).contains("TAG");
    }

    private UnifiedSearchServiceImpl buildService() {
        AppSearchProperties props = new AppSearchProperties();
        return new UnifiedSearchServiceImpl(kbSegmentSearchPort, kbQueryEmbeddingService, props);
    }

    private KbSegmentDocument buildDoc(String segmentId,
                                       String assetType,
                                       String segmentType,
                                       String title,
                                       String contentText,
                                       String ocrText,
                                       Integer pageNo) {
        KbSegmentDocument doc = new KbSegmentDocument();
        doc.setSegmentId(segmentId);
        doc.setAssetId("asset-" + segmentId);
        doc.setAssetType(assetType);
        doc.setSegmentType(segmentType);
        doc.setTitle(title);
        doc.setContentText(contentText);
        doc.setOcrText(ocrText);
        doc.setPageNo(pageNo);
        if ("IMAGE_OCR_BLOCK".equals(segmentType) || "IMAGE_CAPTION".equals(segmentType)) {
            doc.setTags(List.of("mysql"));
        }
        if ("TEXT_CHUNK".equals(segmentType)) {
            doc.setChunkOrder(0);
        }
        return doc;
    }
}
