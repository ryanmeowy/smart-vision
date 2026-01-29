package com.smart.vision.core;

import com.smart.vision.core.config.OCRConfig;
import com.smart.vision.core.config.OSSConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication
@EnableConfigurationProperties({OSSConfig.class, OCRConfig.class})
public class SmartVisionApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartVisionApplication.class, args);
    }

}
