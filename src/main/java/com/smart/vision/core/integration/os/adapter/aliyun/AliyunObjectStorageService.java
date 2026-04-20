package com.smart.vision.core.integration.os.adapter.aliyun;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.ObjectListing;
import com.smart.vision.core.common.exception.ApiError;
import com.smart.vision.core.common.exception.InfraException;
import com.smart.vision.core.integration.os.adapter.aliyun.config.AliyunObjectStorageConfig;
import com.smart.vision.core.integration.os.port.ObjectStoragePort;
import java.net.URL;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class AliyunObjectStorageService implements ObjectStoragePort {

    private final OSS ossClient;
    private final AliyunObjectStorageConfig aliyunObjectStorageConfig;

    @Override
    public String buildPresignedUrl(String objectKey, Long validityTimeMs) {
        Date expiration = new Date(System.currentTimeMillis() + validityTimeMs);
        URL url = ossClient.generatePresignedUrl(aliyunObjectStorageConfig.getBucketName(), objectKey, expiration);
        return url.toString();
    }

    @Override
    public String buildAiPresignedUrl(String objectKey, Long validityTimeMs) {
        Date expiration = new Date(System.currentTimeMillis() + validityTimeMs);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                aliyunObjectStorageConfig.getBucketName(),
                objectKey,
                HttpMethod.GET
        );
        request.setExpiration(expiration);
        String processParam = aliyunObjectStorageConfig.getImageProcessEmbedding();
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
            ossClient.putObject(aliyunObjectStorageConfig.getBucketName(), fileName, file.getInputStream());
            return fileName;
        } catch (Exception e) {
            log.error("Failed to upload file to OSS: {}", e.getMessage(), e);
            throw new InfraException(ApiError.INTERNAL_ERROR, "Failed to upload file to object storage.", e);
        }
    }

    @Override
    public void deleteByFolder(String folderName) {
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest(aliyunObjectStorageConfig.getBucketName())
                .withPrefix(folderName);
        ObjectListing objectListing;
        do {
            objectListing = ossClient.listObjects(listObjectsRequest);
            objectListing.getObjectSummaries().forEach(objectSummary -> {
                String objectKey = objectSummary.getKey();
                ossClient.deleteObject(aliyunObjectStorageConfig.getBucketName(), objectKey);
                log.info("Deleted object: {}", objectKey);
            });
            listObjectsRequest.setMarker(objectListing.getNextMarker());
        } while (objectListing.isTruncated());
    }
}
