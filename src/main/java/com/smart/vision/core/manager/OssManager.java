package com.smart.vision.core.manager;

import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class OssManager {
    // 上传文件流，返回公网 URL
    public String uploadFile(InputStream inputStream, String fileName) {
        return "";
    }
}