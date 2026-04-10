package com.smart.vision.core.search.escli.application;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.vision.core.common.exception.BusinessException;
import com.smart.vision.core.search.escli.domain.EsCliAccessControl;
import com.smart.vision.core.search.escli.interfaces.rest.dto.EsDocSearchRequestDTO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class EsCliQueryServiceTest {

    @Mock
    private ElasticsearchClient esClient;
    @Mock
    private EsCliAccessControl accessControl;

    private EsCliQueryService service;

    @BeforeEach
    void setUp() {
        service = new EsCliQueryService(esClient, new ObjectMapper(), accessControl, new SimpleMeterRegistry());
    }

    @Test
    void listIndices_shouldRejectInvalidPatternBeforeEsCall() {
        assertThatThrownBy(() -> service.listIndices("bad pattern", null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid index pattern");
    }

    @Test
    void getIndexStats_shouldRejectInvalidIndexBeforeEsCall() {
        assertThatThrownBy(() -> service.getIndexStats("smart_*"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid index name");
    }

    @Test
    void searchDocuments_shouldRejectUnsupportedSourceField() {
        EsDocSearchRequestDTO request = new EsDocSearchRequestDTO();
        request.setQuery("fileName:cat");
        request.setSourceIncludes(java.util.List.of("imageEmbedding"));

        assertThatThrownBy(() -> service.searchDocuments("smart_gallery_local_read", request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("sourceIncludes contains unsupported fields");
    }

    @Test
    void searchDocuments_shouldRejectUnsafeQueryChars() {
        EsDocSearchRequestDTO request = new EsDocSearchRequestDTO();
        request.setQuery("fileName:cat;DROP TABLE");

        assertThatThrownBy(() -> service.searchDocuments("smart_gallery_local_read", request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("query contains unsupported characters");
    }
}
