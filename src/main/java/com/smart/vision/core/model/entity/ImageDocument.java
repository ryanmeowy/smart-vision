package com.smart.vision.core.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.List;

import static com.smart.vision.core.constant.CommonConstant.IMAGE_INDEX;

/**
 * image doc model for elasticsearch
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Data
@Document(indexName = IMAGE_INDEX) // index name
public class ImageDocument {
    /**
     * Image ID (ES Doc ID)
     */
    @Id
    private String id;

    /**
     * Image path (relative)
     */
    private String imagePath;

    /**
     * OCR extracted text
     */
    private String ocrContent;

    /**
     * Core vector field, dims correspond to Aliyun model dimensions
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

    /**
     * Temporary score field
     */
    @Transient
    private Double score;
}