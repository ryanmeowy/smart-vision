package com.smart.vision.core.model.dto;

import lombok.Data;

/**
 * Batch Process Request Data Transfer Object
 * Represents a request for batch processing of a single image item in the system.
 * This DTO contains the necessary information to locate and process an image
 * that has been uploaded to Alibaba Cloud OSS.
 *
 * @author Ryan
 * @since 2025/12/18
 */
@Data
public class BatchProcessDTO {

    /**
     * OSS Object Key
     * example: images/2024/abc.jpg
     */
    private String key;

    /**
     * original file name
     */
    private String fileName;

    /**
     * File fingerprint (MD5)
     */
    private String fileHash;

}