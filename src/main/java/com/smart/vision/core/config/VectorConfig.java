package com.smart.vision.core.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("vectorConfig")
@Getter
public class VectorConfig {

    @Value("${app.vector.index-name}")
    private String indexName;

    @Value("${app.vector.dimension}")
    private int dimension;
}