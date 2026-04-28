package com.smart.vision.core.search.interfaces.rest.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Explain payload for kb search result.
 */
@Data
@Builder
public class KbSearchExplainDTO implements Serializable {

    /**
     * Effective strategy marker for diagnostics.
     */
    private String strategyEffective;

    /**
     * Coarse match sources.
     */
    private List<String> hitSources;

    /**
     * Field-level match flags.
     */
    private MatchedBy matchedBy;

    @Data
    @Builder
    public static class MatchedBy implements Serializable {
        private boolean vector;
        private boolean title;
        private boolean content;
        private boolean ocr;
    }
}
