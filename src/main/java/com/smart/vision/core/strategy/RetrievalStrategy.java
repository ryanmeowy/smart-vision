package com.smart.vision.core.strategy;

import com.smart.vision.core.model.dto.SearchQueryDTO;
import com.smart.vision.core.model.entity.ImageDocument;
import com.smart.vision.core.model.enums.StrategyType;

import java.util.List;

public interface RetrievalStrategy {
    /**
     * 执行搜索
     *
     * @param query       查询请求
     * @param queryVector 用户问题的向量 (可能为空)
     * @return 命中的文档列表
     */
    List<ImageDocument> search(SearchQueryDTO query, List<Float> queryVector);


    /**
     * 获取策略类型
     * @return 策略类型
     */
    StrategyType getType();
}