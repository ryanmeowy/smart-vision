package com.smart.vision.core.conversation.application.impl;

import com.smart.vision.core.common.exception.ApiError;
import com.smart.vision.core.common.exception.BusinessException;
import com.smart.vision.core.conversation.application.ConversationService;
import com.smart.vision.core.conversation.domain.model.ConversationRole;
import com.smart.vision.core.conversation.domain.model.ConversationSession;
import com.smart.vision.core.conversation.domain.model.ConversationTurn;
import com.smart.vision.core.conversation.domain.repository.ConversationRepository;
import com.smart.vision.core.conversation.interfaces.rest.dto.ConversationCreateRequestDTO;
import com.smart.vision.core.conversation.interfaces.rest.dto.ConversationMessageRequestDTO;
import com.smart.vision.core.conversation.interfaces.rest.dto.ConversationMessageResponseDTO;
import com.smart.vision.core.conversation.interfaces.rest.dto.ConversationSessionDTO;
import com.smart.vision.core.conversation.interfaces.rest.dto.ConversationTurnDTO;
import com.smart.vision.core.conversation.interfaces.rest.dto.ConversationTurnListDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Default conversation application service.
 */
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private static final int DEFAULT_TURN_LIMIT = 20;
    private static final int MAX_TURN_LIMIT = 100;

    private final ConversationRepository conversationRepository;

    @Override
    public ConversationSessionDTO createSession(ConversationCreateRequestDTO request) {
        long now = System.currentTimeMillis();
        String title = safeTrim(request.getTitle());
        ConversationSession session = ConversationSession.createActive(newSessionId(), title, now);
        conversationRepository.saveSession(session);
        return toSessionDto(session);
    }

    @Override
    public ConversationSessionDTO getSession(String sessionId) {
        ConversationSession session = loadSessionOrThrow(sessionId);
        return toSessionDto(session);
    }

    @Override
    public ConversationMessageResponseDTO createMessage(String sessionId, ConversationMessageRequestDTO request) {
        ConversationSession session = loadSessionOrThrow(sessionId);
        long now = System.currentTimeMillis();

        ConversationTurn turn = new ConversationTurn();
        turn.setTurnId(newTurnId());
        turn.setSessionId(session.getSessionId());
        turn.setRole(ConversationRole.USER);
        turn.setQuery(request.getQuery().trim());
        turn.setRewrittenQuery(request.getQuery().trim());
        turn.setAnswer("");
        turn.setCitationsJson("[]");
        turn.setRetrievalTraceJson(buildRetrievalTraceJson(request));
        turn.setCreatedAt(now);
        conversationRepository.saveTurn(turn);

        session.touch(now);
        conversationRepository.saveSession(session);

        ConversationMessageResponseDTO response = new ConversationMessageResponseDTO();
        response.setSessionId(session.getSessionId());
        response.setTurnId(turn.getTurnId());
        response.setRewrittenQuery(turn.getRewrittenQuery());
        response.setAnswer(turn.getAnswer());
        response.setCitations(List.of());
        response.setSuggestedQuestions(List.of());

        ConversationMessageResponseDTO.RetrievalTraceDTO traceDTO = new ConversationMessageResponseDTO.RetrievalTraceDTO();
        traceDTO.setTopK(request.getTopK());
        traceDTO.setLimit(request.getLimit());
        traceDTO.setStrategy(request.getStrategy());
        response.setRetrievalTrace(traceDTO);
        response.setCreatedAt(now);
        return response;
    }

    @Override
    public ConversationTurnListDTO listMessages(String sessionId, Integer limit, String beforeTurnId) {
        ConversationSession session = loadSessionOrThrow(sessionId);
        int boundedLimit = normalizeLimit(limit);
        List<ConversationTurn> candidates = conversationRepository.findRecentTurns(session.getSessionId(), MAX_TURN_LIMIT);
        if (candidates.isEmpty()) {
            ConversationTurnListDTO empty = new ConversationTurnListDTO();
            empty.setSessionId(session.getSessionId());
            return empty;
        }

        List<ConversationTurn> filteredTurns = filterBeforeTurn(candidates, session.getSessionId(), beforeTurnId);
        filteredTurns.sort(Comparator.comparingLong(ConversationTurn::getCreatedAt));
        if (filteredTurns.size() > boundedLimit) {
            filteredTurns = filteredTurns.subList(filteredTurns.size() - boundedLimit, filteredTurns.size());
        }

        ConversationTurnListDTO response = new ConversationTurnListDTO();
        response.setSessionId(session.getSessionId());
        response.setTurns(filteredTurns.stream().map(this::toTurnDto).toList());
        return response;
    }

    private List<ConversationTurn> filterBeforeTurn(List<ConversationTurn> candidates, String sessionId, String beforeTurnId) {
        if (!StringUtils.hasText(beforeTurnId)) {
            return new ArrayList<>(candidates);
        }
        ConversationTurn beforeTurn = conversationRepository.findTurn(sessionId, beforeTurnId)
                .orElseThrow(() -> new IllegalArgumentException("beforeTurnId is invalid"));
        long boundary = beforeTurn.getCreatedAt();
        return candidates.stream()
                .filter(turn -> turn.getCreatedAt() < boundary)
                .toList();
    }

    private ConversationSession loadSessionOrThrow(String sessionId) {
        return conversationRepository.findSession(sessionId)
                .orElseThrow(() -> new BusinessException(ApiError.CONVERSATION_SESSION_NOT_FOUND));
    }

    private ConversationSessionDTO toSessionDto(ConversationSession session) {
        ConversationSessionDTO dto = new ConversationSessionDTO();
        dto.setSessionId(session.getSessionId());
        dto.setTitle(session.getTitle());
        dto.setStatus(session.getStatus().name());
        dto.setCreatedAt(session.getCreatedAt());
        dto.setUpdatedAt(session.getUpdatedAt());
        return dto;
    }

    private ConversationTurnDTO toTurnDto(ConversationTurn turn) {
        ConversationTurnDTO dto = new ConversationTurnDTO();
        dto.setTurnId(turn.getTurnId());
        dto.setSessionId(turn.getSessionId());
        dto.setQuery(turn.getQuery());
        dto.setRewrittenQuery(turn.getRewrittenQuery());
        dto.setAnswer(turn.getAnswer());
        dto.setCitations(List.of());
        dto.setCreatedAt(turn.getCreatedAt());
        return dto;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_TURN_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_TURN_LIMIT));
    }

    private String safeTrim(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.trim();
    }

    private String buildRetrievalTraceJson(ConversationMessageRequestDTO request) {
        return String.format(
                "{\"topK\":%s,\"limit\":%s,\"strategy\":%s}",
                request.getTopK() == null ? "null" : request.getTopK(),
                request.getLimit() == null ? "null" : request.getLimit(),
                request.getStrategy() == null ? "null" : "\"" + escapeJson(request.getStrategy()) + "\""
        );
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String newSessionId() {
        return "cvs_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String newTurnId() {
        return "turn_" + UUID.randomUUID().toString().replace("-", "");
    }
}
