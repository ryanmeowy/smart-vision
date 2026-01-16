package com.smart.vision.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.vector")
public class VectorConfig {
    private String indexName;
    private Integer dimension;
}