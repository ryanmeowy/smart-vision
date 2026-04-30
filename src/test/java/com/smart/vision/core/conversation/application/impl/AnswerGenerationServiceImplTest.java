package com.smart.vision.core.conversation.application.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.vision.core.conversation.domain.model.ConversationCitation;
import com.smart.vision.core.conversation.domain.port.ConversationRewritePort;
import com.smart.vision.core.search.interfaces.rest.dto.KbSearchResultDTO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AnswerGenerationServiceImplTest {

    @Mock
    private ConversationRewritePort conversationRewritePort;

    private AnswerGenerationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AnswerGenerationServiceImpl(
                conversationRewritePort,
                new ObjectMapper(),
                new SimpleMeterRegistry()
        );
    }

    @Test
    void generate_shouldFallbackWhenNoGroundingSegment() {
        var result = service.generate(
                "那 InnoDB 呢",
                "mysql 架构中的 InnoDB 作用",
                List.of(),
                List.of()
        );

        assertThat(result.isFallbackUsed()).isTrue();
        assertThat(result.getFallbackReason()).isEqualTo("no_evidence_no_grounding_segment");
        assertThat(result.getAnswerText()).contains("建议改写检索问题：mysql 架构中的 InnoDB 作用");
        assertThat(result.getAnswerText()).contains("你可以重试：");
        verifyNoInteractions(conversationRewritePort);
    }

    @Test
    void generate_shouldFallbackWhenEvidenceTooShort() {
        KbSearchResultDTO candidate = KbSearchResultDTO.builder()
                .segmentId("seg_001")
                .score(0.88D)
                .snippet("too short")
                .build();
        ConversationCitation citation = new ConversationCitation();
        citation.setSegmentId("seg_001");
        citation.setSnippet("短证据");

        var result = service.generate(
                "那 InnoDB 呢",
                "mysql 架构中的 InnoDB 作用",
                List.of(candidate),
                List.of(citation)
        );

        assertThat(result.isFallbackUsed()).isTrue();
        assertThat(result.getFallbackReason()).isEqualTo("no_evidence_evidence_too_short");
        assertThat(result.getAnswerText()).contains("建议改写检索问题：mysql 架构中的 InnoDB 作用");
        verifyNoInteractions(conversationRewritePort);
    }

    @Test
    void generate_shouldFallbackWhenRetrievalScoreTooLow() {
        String longEvidence = "InnoDB 支持事务和行级锁，且具备崩溃恢复能力。".repeat(4);
        KbSearchResultDTO candidate = KbSearchResultDTO.builder()
                .segmentId("seg_002")
                .score(0.03D)
                .snippet(longEvidence)
                .build();
        ConversationCitation citation = new ConversationCitation();
        citation.setSegmentId("seg_002");
        citation.setSnippet(longEvidence);

        var result = service.generate(
                "那 InnoDB 呢",
                "mysql 架构中的 InnoDB 作用",
                List.of(candidate),
                List.of(citation)
        );

        assertThat(result.isFallbackUsed()).isTrue();
        assertThat(result.getFallbackReason()).isEqualTo("no_evidence_low_retrieval_score");
        assertThat(result.getAnswerText()).contains("建议改写检索问题：mysql 架构中的 InnoDB 作用");
        verifyNoInteractions(conversationRewritePort);
    }
}
