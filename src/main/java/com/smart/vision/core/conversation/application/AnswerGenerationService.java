package com.smart.vision.core.conversation.application;

import com.smart.vision.core.conversation.application.model.AnswerGenerationResult;
import com.smart.vision.core.conversation.domain.model.ConversationCitation;
import com.smart.vision.core.search.interfaces.rest.dto.KbSearchResultDTO;

import java.util.List;

/**
 * Generates grounded answer from retrieval evidence.
 */
public interface AnswerGenerationService {

    AnswerGenerationResult generate(String userQuery,
                                    String rewrittenQuery,
                                    List<KbSearchResultDTO> topCandidates,
                                    List<ConversationCitation> citations);
}

