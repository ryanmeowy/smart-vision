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

    /**
     * Physical index version suffix (e.g. v1/v2).
     * <p>
     * Physical index name = indexName + "_" + indexVersion (when indexVersion is not blank).
     */
    private String indexVersion;

    public String getPhysicalIndexName() {
        if (indexVersion == null || indexVersion.isBlank()) {
            return indexName;
        }
        return indexName + "_" + indexVersion;
    }
}