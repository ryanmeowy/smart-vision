package com.smart.vision.core.conversation.domain.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Conversation session aggregate root.
 */
@Data
@NoArgsConstructor
public class ConversationSession {

    private String sessionId;
    private String title;
    private ConversationSessionStatus status;
    private long createdAt;
    private long updatedAt;

    public static ConversationSession createActive(String sessionId, String title, long now) {
        ConversationSession session = new ConversationSession();
        session.setSessionId(sessionId);
        session.setTitle(title);
        session.setStatus(ConversationSessionStatus.ACTIVE);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        return session;
    }

    public void touch(long now) {
        this.updatedAt = now;
    }
}

