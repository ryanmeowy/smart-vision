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
        AppSearchProperties props = new AppSearchProperties();
        UnifiedSearchServiceImpl service = new UnifiedSearchServiceImpl(kbSegmentSearchPort, kbQueryEmbeddingService, props);

        KbSearchQueryDTO query = new KbSearchQueryDTO();
        query.setQuery("mysql");
        query.setTopK(5);
        query.setLimit(3);

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
        assertThat(results.getFirst().getResultType()).isEqualTo("TEXT_CHUNK");
        assertThat(results.getFirst().getSnippet()).contains("mysql");
        assertThat(results.getFirst().getExplain().getMatchedBy().isVector()).isTrue();
        assertThat(results.getFirst().getExplain().getMatchedBy().isContent()).isTrue();
        assertThat(results.get(1).getAssetType()).isEqualTo("IMAGE");
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
        return doc;
    }
}
