package com.smart.vision.core.search.domain.model;

import com.smart.vision.core.search.infrastructure.persistence.es.document.KbSegmentDocument;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Retrieval hit from kb_segment index.
 */
@Value
@Builder
public class KbSegmentHit {
    KbSegmentDocument document;
    double rawScore;
    Map<String, String> highlights;
    List<String> highlightFields;
}
