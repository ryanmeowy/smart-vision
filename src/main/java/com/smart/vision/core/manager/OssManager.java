package com.smart.vision.core.manager;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.smart.vision.core.config.OSSConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.net.URL;
import java.util.Date;

/**
 * Aliyun OSS service
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OssManager {

    private final OSS ossClient;
    @Qualifier("OSSConfig")
    private final OSSConfig ossConfig;

    /**
     * Generate signed temporary access URL
     *
     * @param path         File path in OSS
     * @param validityTime Validity period, unit: ms
     * @return Signed URL
     */
    public String getPresignedUrl(String path, Long validityTime) {
        Date expiration = new Date(System.currentTimeMillis() + validityTime);
        URL url = ossClient.generatePresignedUrl(ossConfig.getBucketName(), path, expiration);
        return url.toString();
    }


    /**
     * [New] URL generation method specifically for AI
     * Automatically appends image compression parameters to limit the image size to within 3MB
     */
    public String getAiPresignedUrl(String objectKey, Long validityTime, String processParam) {
        Date expiration = new Date(System.currentTimeMillis() + validityTime);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                ossConfig.getBucketName(),
                objectKey,
                HttpMethod.GET);
        request.setExpiration(expiration);
        // [Core] Add OSS image processing parameters (x-oss-process)
        // Strategy: Limit maximum width to 1024px, compress quality to 90%
        // This size is sufficient for embedding semantic understanding and typically only takes up a few hundred KB
        request.addQueryParameter("x-oss-process", processParam);
        URL url = ossClient.generatePresignedUrl(request);
        return url.toString();
    }

    public String uploadFile(MultipartFile file) {
        String fileName = "temp/" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
        try {
            ossClient.putObject(ossConfig.getBucketName(), fileName, file.getInputStream());
            return fileName;
        } catch (Exception e) {
            log.error("Failed to upload file to OSS: {}", e.getMessage(), e);
            return null;
        }
    }
}