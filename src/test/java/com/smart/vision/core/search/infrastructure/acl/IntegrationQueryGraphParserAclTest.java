package com.smart.vision.core.search.infrastructure.acl;

import com.smart.vision.core.integration.ai.port.ContentGenerationService;
import com.smart.vision.core.search.domain.model.GraphTriple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationQueryGraphParserAclTest {

    @Mock
    private ContentGenerationService contentGenerationService;

    @Test
    void parseFromKeyword_shouldReturnTriplesWhenIntegrationSucceeded() {
        IntegrationQueryGraphParserAcl acl = new IntegrationQueryGraphParserAcl(contentGenerationService);
        List<GraphTriple> triples = List.of(new GraphTriple("cat", "on", "sofa"));
        when(contentGenerationService.praseTriplesFromKeyword("cat sofa")).thenReturn(triples);

        List<GraphTriple> results = acl.parseFromKeyword("cat sofa");

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getS()).isEqualTo("cat");
    }

    @Test
    void parseFromKeyword_shouldReturnEmptyWhenIntegrationFailed() {
        IntegrationQueryGraphParserAcl acl = new IntegrationQueryGraphParserAcl(contentGenerationService);
        when(contentGenerationService.praseTriplesFromKeyword("cat"))
                .thenThrow(new RuntimeException("service error"));

        List<GraphTriple> results = acl.parseFromKeyword("cat");

        assertThat(results).isEmpty();
    }
}
