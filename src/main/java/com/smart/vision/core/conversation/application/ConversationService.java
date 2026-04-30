package com.smart.vision.core.conversation.application;

import com.smart.vision.core.conversation.interfaces.rest.dto.ConversationCreateRequestDTO;
import com.smart.vision.core.conversation.interfaces.rest.dto.ConversationMessageRequestDTO;
import com.smart.vision.core.conversation.interfaces.rest.dto.ConversationMessageResponseDTO;
import com.smart.vision.core.conversation.interfaces.rest.dto.ConversationSessionDTO;
import com.smart.vision.core.conversation.interfaces.rest.dto.ConversationTurnListDTO;

/**
 * Application service for conversation APIs.
 */
public interface ConversationService {

    ConversationSessionDTO createSession(ConversationCreateRequestDTO request);

    ConversationSessionDTO getSession(String sessionId);

    ConversationMessageResponseDTO createMessage(String sessionId, ConversationMessageRequestDTO request);

    ConversationTurnListDTO listMessages(String sessionId, Integer limit, String beforeTurnId);
}

