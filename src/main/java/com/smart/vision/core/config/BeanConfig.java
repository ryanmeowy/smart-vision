package com.smart.vision.core.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bean configuration clazz
 *
 * @author Ryan
 * @since 2025/12/16
 */
@Configuration
@RequiredArgsConstructor
public class BeanConfig {

    private final OssConfig ossConfig;

    /**
     * Register Aliyun OSS client
     * Scope: Singleton
     */
    @Bean(destroyMethod = "shutdown")
    public OSS ossClient() {
        return new OSSClientBuilder().build(
                ossConfig.getEndpoint(),
                ossConfig.getAccessKeyId(),
                ossConfig.getAccessKeySecret()
        );
    }
}