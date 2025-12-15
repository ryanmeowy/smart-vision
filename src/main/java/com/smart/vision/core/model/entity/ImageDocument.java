package com.smart.vision.core.model.entity;

import lombok.Data;

import java.util.List;

/**
 * image doc model for elasticsearch
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Data
public class ImageDocument {
    /**
     * Image ID (ES Doc ID)
     */
    private String id;

    /**
     * Image access URL (OSS URL)
     */
    private String url;

    /**
     * OCR extracted text
     */
    private String ocrContent;

    /**
     * Core vector field, dims correspond to Alibaba Cloud model dimensions
     */
    private List<Float> imageEmbedding;

    /**
     * Creation time
     */
    private Long createTime;

    /**
     * Filename
     */
    private String filename;

}