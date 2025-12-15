package com.smart.vision.core.repository;

import com.smart.vision.core.model.entity.ImageDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Elasticsearch 数据访问层
 * 泛型 1: 实体类
 * 泛型 2: 主键类型 (ES ID 是 String)
 */
@Repository
public interface ImageRepository extends ElasticsearchRepository<ImageDocument, String>, ImageRepositoryCustom {

    // 基础 CRUD 由父接口提供，无需手写
    // save(doc)
    // findById(id)
    
    /**
     * 简单的根据 URL 查询 (用于去重检查)
     */
    List<ImageDocument> findByUrl(String url);

    /**
     * 仅根据 OCR 内容进行简单的全文检索 (不含向量)
     * 真正复杂的混合检索建议在 Service/Strategy 层使用 ElasticsearchClient 构建
     */
    List<ImageDocument> findByOcrContentMatches(String text);
}