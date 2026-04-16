package com.smart.vision.core.integration.os.port;

import org.springframework.web.multipart.MultipartFile;

/**
 * Generic object storage capability contract.
 */
public interface ObjectStorageService {

    String buildPresignedUrl(String objectKey, Long validityTimeMs);

    String buildAiPresignedUrl(String objectKey, Long validityTimeMs, String processParam);

    String uploadFile(MultipartFile file);

    void deleteByFolder(String folderName);
}
