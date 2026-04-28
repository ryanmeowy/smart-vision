package com.smart.vision.core.search.domain.port;

import com.smart.vision.core.search.domain.model.KbSegmentHit;

import java.util.List;

/**
 * Search port for unified kb_segment retrieval.
 */
public interface KbSegmentSearchPort {

    List<KbSegmentHit> textSearch(String query, int limit);

    List<KbSegmentHit> vectorSearch(List<Float> queryVector, int topK);
}
