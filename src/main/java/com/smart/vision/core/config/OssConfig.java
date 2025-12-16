package com.smart.vision.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OSS configuration clazz
 *
 * @author Ryan
 * @since 2025/12/16
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssConfig {
    /**
     * Internal network endpoint, used for uploading files
     */
    private String endpoint;
    /**
     * Public network endpoint, used for accessing files
     */
    private String publicEndpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;
}