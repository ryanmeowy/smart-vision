package com.smart.vision.core;

import com.smart.vision.core.config.OssConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(OssConfig.class)
public class SmartVisionApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartVisionApplication.class, args);
    }

}
