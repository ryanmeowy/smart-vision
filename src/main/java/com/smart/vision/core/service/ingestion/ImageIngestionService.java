package com.smart.vision.core.service.ingestion;

import com.smart.vision.core.model.dto.BatchProcessDTO;
import com.smart.vision.core.model.dto.BatchUploadResultDTO;

import java.util.List;

/**
 * Image data processing service
 * Provides functionality for processing and indexing image data in the system
 * Main operations include batch processing of images that have been uploaded to OSS
 *
 * @author Ryan
 * @since 2025/12/15
 */
public interface ImageIngestionService {

    /**
     * Processes a batch of image items for vector indexing and storage
     *
     * @param items list of batch process requests containing OSS keys and metadata
     * @return BatchUploadResultDTO containing processing statistics and results
     */
    BatchUploadResultDTO processBatchItems(List<BatchProcessDTO> items);
}