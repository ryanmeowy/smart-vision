package com.smart.vision.core.integration.ai.adapter.local;

import com.smart.vision.core.grpc.VisionProto;
import com.smart.vision.core.grpc.VisionServiceGrpc;
import com.smart.vision.core.integration.ai.port.CrossEncoderRerankService.RerankResult;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalCrossEncoderRerankImplTest {

    @Mock
    private VisionServiceGrpc.VisionServiceBlockingStub visionStub;
    @Mock
    private VisionServiceGrpc.VisionServiceBlockingStub deadlineStub;

    private LocalCrossEncoderRerankImpl service;

    @BeforeEach
    void setUp() {
        service = new LocalCrossEncoderRerankImpl();
        ReflectionTestUtils.setField(service, "visionStub", visionStub);
        ReflectionTestUtils.setField(service, "rerankDeadlineMs", 3000L);
    }

    @Test
    void rerank_shouldUseGrpcResults_whenGrpcAvailable() {
        when(visionStub.withDeadlineAfter(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(deadlineStub);
        VisionProto.RerankResponse response = VisionProto.RerankResponse.newBuilder()
                .addResults(VisionProto.RerankResult.newBuilder().setIndex(2).setRelevanceScore(0.82f).build())
                .addResults(VisionProto.RerankResult.newBuilder().setIndex(1).setRelevanceScore(0.91f).build())
                .build();
        when(deadlineStub.rerank(any(VisionProto.RerankRequest.class))).thenReturn(response);

        List<RerankResult> results = service.rerank("cat sofa", List.of("a", "b", "c"), 2);

        assertThat(results).hasSize(2);
        assertThat(results.getFirst().index()).isEqualTo(1);
        assertThat(results.get(1).index()).isEqualTo(2);
    }

    @Test
    void rerank_shouldFallbackToLexical_whenGrpcFails() {
        when(visionStub.withDeadlineAfter(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(deadlineStub);
        when(deadlineStub.rerank(any(VisionProto.RerankRequest.class)))
                .thenThrow(Status.DEADLINE_EXCEEDED.asRuntimeException());

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
