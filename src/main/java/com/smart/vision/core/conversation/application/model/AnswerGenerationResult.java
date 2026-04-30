package com.smart.vision.core.conversation.application.model;

import lombok.Data;

/**
 * Generated answer result with fallback metadata.
 */
@Data
public class AnswerGenerationResult {

    private String answerText;
    private boolean fallbackUsed;
    private String fallbackReason;
}

