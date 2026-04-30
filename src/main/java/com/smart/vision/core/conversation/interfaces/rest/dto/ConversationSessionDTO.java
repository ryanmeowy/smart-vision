package com.smart.vision.core.conversation.interfaces.rest.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * Conversation session response DTO.
 */
@Data
public class ConversationSessionDTO implements Serializable {

    private String sessionId;
    private String title;
    private String status;
    private long createdAt;
    private long updatedAt;
}

