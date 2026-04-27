package com.smart.vision.core.ingestion.domain.port;

import com.smart.vision.core.ingestion.domain.model.TextChunk;

import java.util.List;

/**
 * Repository for persisted text segments/chunks.
 */
public interface TextSegmentRepository {

    void save(String assetId, List<TextChunk> chunks);
}
