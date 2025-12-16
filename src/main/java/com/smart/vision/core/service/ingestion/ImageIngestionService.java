package com.smart.vision.core.service.ingestion;

import com.smart.vision.core.model.dto.BatchUploadResultDTO;
import org.springframework.web.multipart.MultipartFile;

/**
 * Image data processing service
 *
 * @author Ryan
 * @since 2025/12/15
 */
public interface ImageIngestionService {

    /**
     * Upload image and index
     *
     * @param file image file
     */
    void processAndIndex(MultipartFile file) throws Exception;

    /**
     * Batch upload images and index
     *
     * @param files image files
     * @return batch upload result
     */
    BatchUploadResultDTO batchProcess(MultipartFile[] files);

}