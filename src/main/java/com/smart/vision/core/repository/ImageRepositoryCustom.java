
package com.smart.vision.core.repository;

import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.model.entity.ImageDocument;

import java.util.List;

/**
 * 自定义扩展接口，用于定义复杂的混合检索方法
 */
public interface ImageRepositoryCustom {
    /**
     * 混合检索：向量 + OCR文本
     *
     * @param keyword     用户输入的文本
     * @param limit       返回条数
     * @param queryVector 文本转换后的向量
     * @return 匹配的文档列表
     */
    List<ImageDocument> hybridSearch(SearchQueryDTO query, List<Float> queryVector);
}