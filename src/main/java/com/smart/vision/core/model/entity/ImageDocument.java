package com.smart.vision.core.model.entity;

import lombok.Data;

import java.util.List;

@Data
public class ImageDocument {
    /**
     * 图片ID (ES Doc ID)
     */
    private String id;

    /**
     * 图片访问地址 (OSS URL)
     */
    private String url;

    /**
     * OCR 提取的文本
     */
    private String ocrContent;

    /**
     * 核心向量字段，dims 对应阿里云模型维度
     */
    private List<Float> imageEmbedding;

    /**
     * 创建时间
     */
    private Long createTime;

    /**
     * 文件名
     */
    private String filename;

}