package com.smart.vision.core.conversation.infrastructure.persistence.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.vision.core.conversation.domain.model.ConversationSession;
import com.smart.vision.core.conversation.domain.model.ConversationTurn;
import com.smart.vision.core.conversation.domain.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis repository for conversation session and turn storage.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisConversationRepository implements ConversationRepository {

    private static final String SESSION_KEY_PREFIX = "conversation:session:";
    private static final String TURN_KEY_PREFIX = "conversation:turn:";
    private static final String TURN_INDEX_KEY_PREFIX = "conversation:turn:index:";
    private static final long DEFAULT_TTL_SECONDS = TimeUnit.DAYS.toSeconds(30);
    private static final int MAX_RECENT_LIMIT = 100;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void saveSession(ConversationSession session) {
        if (session == null || !StringUtils.hasText(session.getSessionId())) {
            throw new IllegalArgumentException("sessionId cannot be empty");
        }
        redisTemplate.opsForValue().set(
                sessionKey(session.getSessionId()),
                toJson(session),
                DEFAULT_TTL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    @Override
    public Optional<ConversationSession> findSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Optional.empty();
        }
        String key = sessionKey(sessionId);
        String raw = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(raw, ConversationSession.class));
        } catch (Exception e) {
            log.warn("Failed to parse conversation session, key={}", key, e);
            redisTemplate.delete(key);
            return Optional.empty();
        }
    }

    @Override
    public void saveTurn(ConversationTurn turn) {
        if (turn == null || !StringUtils.hasText(turn.getSessionId()) || !StringUtils.hasText(turn.getTurnId())) {
            throw new IllegalArgumentException("sessionId and turnId cannot be empty");
        }
        String turnKey = turnKey(turn.getSessionId(), turn.getTurnId());
        redisTemplate.opsForValue().set(turnKey, toJson(turn), DEFAULT_TTL_SECONDS, TimeUnit.SECONDS);

        String indexKey = turnIndexKey(turn.getSessionId());
        redisTemplate.opsForZSet().add(indexKey, turn.getTurnId(), turn.getCreatedAt());
        redisTemplate.expire(indexKey, DEFAULT_TTL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public Optional<ConversationTurn> findTurn(String sessionId, String turnId) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(turnId)) {
            return Optional.empty();
        }
        return loadTurn(sessionId, turnId);
    }

    @Override
    public List<ConversationTurn> findRecentTurns(String sessionId, int limit) {
        if (!StringUtils.hasText(sessionId)) {
            return List.of();
        }
        int boundedLimit = Math.max(1, Math.min(limit, MAX_RECENT_LIMIT));
        Set<String> turnIds = redisTemplate.opsForZSet()
                .reverseRange(turnIndexKey(sessionId), 0, boundedLimit - 1);
        if (turnIds == null || turnIds.isEmpty()) {
            return List.of();
        }

        List<ConversationTurn> turns = new ArrayList<>();
        for (String turnId : turnIds) {
            loadTurn(sessionId, turnId).ifPresent(turns::add);
        }
        return turns;
    }

    private Optional<ConversationTurn> loadTurn(String sessionId, String turnId) {
        String key = turnKey(sessionId, turnId);
        String raw = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(raw)) {
            redisTemplate.opsForZSet().remove(turnIndexKey(sessionId), turnId);
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(raw, ConversationTurn.class));
        } catch (Exception e) {
            log.warn("Failed to parse conversation turn, key={}", key, e);
            redisTemplate.delete(key);
            redisTemplate.opsForZSet().remove(turnIndexKey(sessionId), turnId);
            return Optional.empty();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize conversation payload.", e);
        }
    }

    private String sessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    private String turnKey(String sessionId, String turnId) {
        return TURN_KEY_PREFIX + sessionId + ":" + turnId;
    }

    private String turnIndexKey(String sessionId) {
        return TURN_INDEX_KEY_PREFIX + sessionId;
    }
}

