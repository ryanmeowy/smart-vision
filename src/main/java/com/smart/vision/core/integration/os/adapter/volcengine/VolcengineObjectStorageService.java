package com.smart.vision.core.integration.os.adapter.volcengine;

import com.smart.vision.core.integration.os.port.ObjectStoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.capability-provider", name = "object-storage", havingValue = "volcengine")
public class VolcengineObjectStorageService implements ObjectStoragePort {
    @Override
    public String buildPresignedUrl(String objectKey, Long validityTimeMs) {
        // TODO Unrealized
        throw new UnsupportedOperationException("Unrealized");
    }

    @Override
    public String buildAiPresignedUrl(String objectKey, Long validityTimeMs) {
        // TODO Unrealized
        throw new UnsupportedOperationException("Unrealized");
    }

    @Override
    public String uploadFile(MultipartFile file) {
        // TODO Unrealized
        throw new UnsupportedOperationException("Unrealized");
    }

    @Override
    public void deleteByFolder(String folderName) {
        // TODO Unrealized
        throw new UnsupportedOperationException("Unrealized");
    }
}
