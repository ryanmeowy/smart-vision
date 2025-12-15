
package com.smart.vision.core.model.enums;

/**
 * 检索策略类型枚举
 */
public enum StrategyType {

    /**
     * 混合检索 (默认)：向量相似度 + OCR 文本匹配
     * 适用场景：通用搜索，即想搜画面，又想搜文字
     */
    HYBRID,

    /**
     * 纯向量检索
     * 适用场景：以文搜图 (描述画面感)，忽略 OCR
     */
    VECTOR_ONLY,

    /**
     * 纯文本检索 (BM25)
     * 适用场景：仅搜索图片内的文字
     */
    TEXT_ONLY,

    /**
     * 以图搜图
     * 适用场景：用户上传一张图，搜相似图
     */
    IMAGE_TO_IMAGE;
}