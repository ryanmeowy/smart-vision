package com.smart.vision.core.integration.ai.adapter.local;

import com.smart.vision.core.integration.ai.port.CrossEncoderRerankService.RerankResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalCrossEncoderRerankImplTest {

    private final LocalCrossEncoderRerankImpl service = new LocalCrossEncoderRerankImpl();

    @Test
    void rerank_shouldSortByLexicalRelevance() {
        List<String> docs = List.of(
                "dog running in the park",
                "cat sleeping on sofa at home",
                "home sofa decor with cat"
        );

        List<RerankResult> results = service.rerank("cat sofa", docs, 2);

        assertThat(results).hasSize(2);
        assertThat(results.getFirst().index()).isEqualTo(1);
        assertThat(results.getFirst().score()).isGreaterThanOrEqualTo(results.get(1).score());
    }

    @Test
    void rerank_shouldReturnEmptyWhenInputInvalid() {
        assertThat(service.rerank("", List.of("a"), 1)).isEmpty();
        assertThat(service.rerank("cat", List.of(), 1)).isEmpty();
    }
}
