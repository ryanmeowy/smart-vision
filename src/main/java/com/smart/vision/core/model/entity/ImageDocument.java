package com.smart.vision.core.model.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

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
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String ocrContent;

    /**
     * Core vector field, dims correspond to Aliyun model dimensions
     */
    @Field(type = FieldType.Dense_Vector, dims = 1024, similarity = "cosine")
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
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String fileName;

    /**
     * AI tags
     */
    @Field(type = FieldType.Keyword)
    private List<String> tags;
}