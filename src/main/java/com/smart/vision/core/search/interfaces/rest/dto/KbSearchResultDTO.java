package com.smart.vision.core.search.interfaces.rest.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * Unified kb search response item.
 */
@Data
@Builder
public class KbSearchResultDTO implements Serializable {

    private String resultType;
    private String assetType;
    private String snippet;
    private Integer pageNo;
    private Double score;
    private KbSearchExplainDTO explain;

    /**
     * Optional trace fields for callback to original asset.
     */
    private String segmentId;
    private String assetId;
    private String sourceRef;
}
