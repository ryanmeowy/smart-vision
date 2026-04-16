package com.smart.vision.core.integration.os.adapter.aliyun;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.ObjectListing;
import com.smart.vision.core.integration.config.aliyun.AliyunOSSConfig;
import com.smart.vision.core.integration.os.port.ObjectStorageService;
import java.net.URL;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Aliyun OSS implementation of object storage capability.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.capability-provider", name = "object-storage", havingValue = "aliyun")
public class AliyunObjectStorageService implements ObjectStorageService {

    private final OSS ossClient;
    private final AliyunOSSConfig aliyunOssConfig;

    @Override
    public String buildPresignedUrl(String objectKey, Long validityTimeMs) {
        Date expiration = new Date(System.currentTimeMillis() + validityTimeMs);
        URL url = ossClient.generatePresignedUrl(aliyunOssConfig.getBucketName(), objectKey, expiration);
        return url.toString();
    }

    @Override
    public String buildAiPresignedUrl(String objectKey, Long validityTimeMs, String processParam) {
        Date expiration = new Date(System.currentTimeMillis() + validityTimeMs);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                aliyunOssConfig.getBucketName(),
                objectKey,
                HttpMethod.GET
        );
        request.setExpiration(expiration);
        if (StringUtils.hasText(processParam)) {
            request.addQueryParameter("x-oss-process", processParam);
        }
        URL url = ossClient.generatePresignedUrl(request);
        return url.toString();
    }

    @Override
    public String uploadFile(MultipartFile file) {
        String fileName = "temp/" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
        try {
            ossClient.putObject(aliyunOssConfig.getBucketName(), fileName, file.getInputStream());
            return fileName;
        } catch (Exception e) {
            log.error("Failed to upload file to OSS: {}", e.getMessage(), e);
            return Strings.EMPTY;
        }
    }

    @Override
    public void deleteByFolder(String folderName) {
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest(aliyunOssConfig.getBucketName())
                .withPrefix(folderName);
        ObjectListing objectListing;
        do {
            objectListing = ossClient.listObjects(listObjectsRequest);
            objectListing.getObjectSummaries().forEach(objectSummary -> {
                String objectKey = objectSummary.getKey();
                ossClient.deleteObject(aliyunOssConfig.getBucketName(), objectKey);
                log.info("Deleted object: {}", objectKey);
            });
            listObjectsRequest.setMarker(objectListing.getNextMarker());
        } while (objectListing.isTruncated());
    }
}
