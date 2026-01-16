package com.smart.vision.core.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.List;

import static com.smart.vision.core.constant.CommonConstant.SMART_GALLERY_V2;

/**
 * image doc model for elasticsearch
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Data
@Document(indexName = SMART_GALLERY_V2, createIndex = false)
public class ImageDocument {
    /**
     * Image ID (ES Doc ID)
     */
    @Id
    @Field(type = FieldType.Long)
    private Long id;

    /**
     * Image path (relative)
     */
    @Field(type = FieldType.Keyword)
    private String imagePath;

    /**
     * OCR extracted text
     */
    @Field(type = FieldType.Text, analyzer = "my_ik_analyzer", searchAnalyzer = "my_ik_search_analyzer")
    private String ocrContent;

    /**
     * Core vector field, dims correspond to Aliyun model dimensions
     */
    @Field(type = FieldType.Dense_Vector, similarity = "cosine", dims = 1024)
    private List<Float> imageEmbedding;

    /**
     * Creation time
     */
    @Field(type = FieldType.Long)
    private Long createTime;

    /**
     * original file name
     */
    @Field(type = FieldType.Keyword)
    private String rawFilename;

    /**
     * file name
     */
    @Field(type = FieldType.Text, analyzer = "my_ik_analyzer", searchAnalyzer = "my_ik_search_analyzer")
    private String fileName;

    /**
     * AI tags
     */
    @Field(type = FieldType.Keyword)
    private List<String> tags;

    /**
     * File fingerprint (MD5)
     */
    @Field(type = FieldType.Keyword)
    private String fileHash;
}