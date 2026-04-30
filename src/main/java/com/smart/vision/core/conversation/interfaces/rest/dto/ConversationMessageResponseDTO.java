package com.smart.vision.core.conversation.interfaces.rest.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Response DTO for conversation message API.
 */
@Data
public class ConversationMessageResponseDTO implements Serializable {

    private String sessionId;
    private String turnId;
    private String rewrittenQuery;
    private String answer;
    private List<ConversationTurnDTO.CitationDTO> citations;
    private RetrievalTraceDTO retrievalTrace;
    private List<String> suggestedQuestions;
    private long createdAt;

    @Data
    public static class RetrievalTraceDTO implements Serializable {
        private Integer topK;
        private Integer limit;
        private String strategy;
    }
}

