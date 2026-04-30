package com.smart.vision.core.conversation.application.model;

import com.smart.vision.core.search.interfaces.rest.dto.KbSearchResultDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Retrieval output for conversation orchestration.
 */
@Data
public class ConversationRetrievalResult {

    private List<KbSearchResultDTO> topCandidates = new ArrayList<>();
    private List<GroupedResult> groupedResults = new ArrayList<>();

    @Data
    public static class GroupedResult {
        private String groupKey;
        private List<KbSearchResultDTO> items = new ArrayList<>();
    }
}

