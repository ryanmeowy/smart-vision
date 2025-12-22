package com.smart.vision.core.config;

import com.aliyun.ocr_api20210707.Client;
import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.comm.Protocol;
import com.aliyun.teaopenapi.models.Config;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
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

    @Qualifier("OSSConfig")
    private final OSSConfig ossConfig;

    @Qualifier("OCRConfig")
    private final OCRConfig ocrConfig;

    /**
     * Register Aliyun OSS client
     * Scope: Singleton
     */
    @Bean(destroyMethod = "shutdown")
    public OSS ossClient() {
        ClientBuilderConfiguration conf = new ClientBuilderConfiguration();
        conf.setProtocol(Protocol.HTTPS);
        return new OSSClientBuilder().build(
                ossConfig.getEndpoint(),
                ossConfig.getAccessKeyId(),
                ossConfig.getAccessKeySecret(),
                conf
        );
    }

    /**
     * Register OCR client
     * Scope: Singleton
     */
    @Bean
    public Client ocrClient() throws Exception {
        Config config = new Config()
                .setAccessKeyId(ocrConfig.getAccessKeyId())
                .setAccessKeySecret(ocrConfig.getAccessKeySecret())
                .setEndpoint(ocrConfig.getEndpoint());

        return new Client(config);
    }
}