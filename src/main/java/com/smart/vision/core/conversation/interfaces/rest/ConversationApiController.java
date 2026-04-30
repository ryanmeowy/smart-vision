package com.smart.vision.core.conversation.interfaces.rest;

import com.smart.vision.core.auth.RequireAuth;
import com.smart.vision.core.common.model.Result;
import com.smart.vision.core.conversation.application.ConversationService;
import com.smart.vision.core.conversation.interfaces.rest.dto.ConversationCreateRequestDTO;
import com.smart.vision.core.conversation.interfaces.rest.dto.ConversationMessageRequestDTO;
import com.smart.vision.core.conversation.interfaces.rest.dto.ConversationMessageResponseDTO;
import com.smart.vision.core.conversation.interfaces.rest.dto.ConversationSessionDTO;
import com.smart.vision.core.conversation.interfaces.rest.dto.ConversationTurnListDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conversation APIs.
 */
@RestController
@Validated
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationApiController {

    private final ConversationService conversationService;

    @RequireAuth
    @PostMapping
    public Result<ConversationSessionDTO> createSession(@Valid @RequestBody ConversationCreateRequestDTO request) {
        return Result.success(conversationService.createSession(request));
    }

    @RequireAuth
    @GetMapping("/{sessionId}")
    public Result<ConversationSessionDTO> getSession(@PathVariable @NotBlank String sessionId) {
        return Result.success(conversationService.getSession(sessionId));
    }

    @RequireAuth
    @PostMapping("/{sessionId}/messages")
    public Result<ConversationMessageResponseDTO> createMessage(
            @PathVariable @NotBlank String sessionId,
            @Valid @RequestBody ConversationMessageRequestDTO request) {
        return Result.success(conversationService.createMessage(sessionId, request));
    }

    @RequireAuth
    @GetMapping("/{sessionId}/messages")
    public Result<ConversationTurnListDTO> listMessages(
            @PathVariable @NotBlank String sessionId,
            @RequestParam(required = false) @Min(1) @Max(100) Integer limit,
            @RequestParam(required = false) String beforeTurnId) {
        return Result.success(conversationService.listMessages(sessionId, limit, beforeTurnId));
    }
}

