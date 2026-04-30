package com.smart.vision.core.conversation.interfaces.rest;

import com.smart.vision.core.common.exception.GlobalExceptionHandler;
import com.smart.vision.core.conversation.application.ConversationService;
import com.smart.vision.core.conversation.interfaces.rest.dto.ConversationCreateRequestDTO;
import com.smart.vision.core.conversation.interfaces.rest.dto.ConversationMessageRequestDTO;
import com.smart.vision.core.conversation.interfaces.rest.dto.ConversationMessageResponseDTO;
import com.smart.vision.core.conversation.interfaces.rest.dto.ConversationSessionDTO;
import com.smart.vision.core.conversation.interfaces.rest.dto.ConversationTurnListDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ConversationApiControllerTest {

    @Mock
    private ConversationService conversationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ConversationApiController(conversationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createSession_shouldReturnResultEnvelope() throws Exception {
        ConversationSessionDTO session = new ConversationSessionDTO();
        session.setSessionId("cvs_test_001");
        session.setStatus("ACTIVE");
        session.setCreatedAt(1777520000000L);
        session.setUpdatedAt(1777520000000L);
        when(conversationService.createSession(eq(new ConversationCreateRequestDTO()))).thenReturn(session);

        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").value("cvs_test_001"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void createMessage_shouldRejectWhenQueryMissing() throws Exception {
        mockMvc.perform(post("/api/conversations/cvs_test_001/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topK\":60,\"limit\":20,\"strategy\":\"KB_RRF_RERANK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid request parameters."));
    }

    @Test
    void listMessages_shouldPassQueryParameters() throws Exception {
        ConversationTurnListDTO list = new ConversationTurnListDTO();
        list.setSessionId("cvs_test_001");
        when(conversationService.listMessages(eq("cvs_test_001"), eq(15), eq("turn_001"))).thenReturn(list);

        mockMvc.perform(get("/api/conversations/cvs_test_001/messages")
                        .param("limit", "15")
                        .param("beforeTurnId", "turn_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").value("cvs_test_001"));
    }

    @Test
    void createMessage_shouldReturnConversationPayload() throws Exception {
        ConversationMessageResponseDTO response = new ConversationMessageResponseDTO();
        response.setSessionId("cvs_test_001");
        response.setTurnId("turn_test_001");
        response.setRewrittenQuery("mysql 架构中的 InnoDB 作用");
        response.setAnswer("InnoDB 是默认事务引擎。[1]");
        response.setCitations(List.of());
        ConversationMessageResponseDTO.RetrievalTraceDTO trace = new ConversationMessageResponseDTO.RetrievalTraceDTO();
        trace.setTopK(60);
        trace.setLimit(20);
        trace.setStrategy("KB_RRF_RERANK");
        trace.setRewriteReason("rewrite_by_model");
        trace.setRetrievedCount(3);
        response.setRetrievalTrace(trace);
        response.setCreatedAt(1777520001000L);

        ConversationMessageRequestDTO request = new ConversationMessageRequestDTO();
        request.setQuery("那 InnoDB 呢");
        request.setTopK(60);
        request.setLimit(20);
        request.setStrategy("KB_RRF_RERANK");
        when(conversationService.createMessage(eq("cvs_test_001"), eq(request))).thenReturn(response);

        mockMvc.perform(post("/api/conversations/cvs_test_001/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "那 InnoDB 呢",
                                  "topK": 60,
                                  "limit": 20,
                                  "strategy": "KB_RRF_RERANK"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.turnId").value("turn_test_001"))
                .andExpect(jsonPath("$.data.retrievalTrace.topK").value(60))
                .andExpect(jsonPath("$.data.retrievalTrace.rewriteReason").value("rewrite_by_model"))
                .andExpect(jsonPath("$.data.retrievalTrace.retrievedCount").value(3));
    }

    @Test
    void listMessages_shouldReturnTurns() throws Exception {
        ConversationTurnListDTO list = new ConversationTurnListDTO();
        list.setSessionId("cvs_test_001");
        when(conversationService.listMessages(eq("cvs_test_001"), eq(20), eq(null))).thenReturn(list);

        mockMvc.perform(get("/api/conversations/cvs_test_001/messages")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").value("cvs_test_001"));
    }
}
