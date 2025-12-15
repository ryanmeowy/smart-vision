package com.smart.vision.core.service.ingestion;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

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
    void processAndIndex(MultipartFile file) throws IOException, ExecutionException, InterruptedException;

}