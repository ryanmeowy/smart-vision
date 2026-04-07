package com.smart.vision.core.search.infrastructure.acl;

import com.smart.vision.core.integration.ai.port.CrossEncoderRerankService;
import com.smart.vision.core.integration.ai.port.CrossEncoderRerankService.RerankResult;
import com.smart.vision.core.search.domain.port.SearchRerankPort.RerankItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationSearchRerankAclTest {

    @Mock
    private CrossEncoderRerankService crossEncoderRerankService;

    @Test
    void rerank_shouldMapAndSortResult() {
        IntegrationSearchRerankAcl acl = new IntegrationSearchRerankAcl(crossEncoderRerankService);
        when(crossEncoderRerankService.rerank("cat", List.of("a", "b", "c"), 3))
                .thenReturn(List.of(
                        new RerankResult(2, 0.71),
                        new RerankResult(1, 0.93)
                ));

        List<RerankItem> results = acl.rerank("cat", List.of("a", "b", "c"), 3);

        assertThat(results).hasSize(2);
        assertThat(results.getFirst().index()).isEqualTo(1);
        assertThat(results.getFirst().score()).isEqualTo(0.93);
    }

    @Test
    void rerank_shouldReturnEmptyWhenIntegrationFails() {
        IntegrationSearchRerankAcl acl = new IntegrationSearchRerankAcl(crossEncoderRerankService);
        when(crossEncoderRerankService.rerank("cat", List.of("a"), 1))
                .thenThrow(new RuntimeException("downstream error"));

        List<RerankItem> results = acl.rerank("cat", List.of("a"), 1);

        assertThat(results).isEmpty();
    }
}
