package com.smart.vision.core.integration.os.port;

import org.springframework.web.multipart.MultipartFile;

/**
 * Top-level object-storage capability port.
 */
public interface ObjectStoragePort {

    String buildPresignedUrl(String objectKey, Long validityTimeMs);

    String buildAiPresignedUrl(String objectKey, Long validityTimeMs);

    String uploadFile(MultipartFile file);

    void deleteByFolder(String folderName);
}
