package com.smart.vision.core.ingestion.infrastructure.acl;

import com.smart.vision.core.ingestion.domain.port.IngestionObjectStoragePort;
import com.smart.vision.core.integration.storage.port.ObjectStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.smart.vision.core.integration.storage.domain.model.PresignedValidityEnum.SHORT_TERM_VALIDITY;

/**
 * ACL adapter from ingestion storage port to integration OSS manager.
 */
@Component
@RequiredArgsConstructor
public class OssIngestionAcl implements IngestionObjectStoragePort {

    private final ObjectStoragePort objectStorageService;

    @Override
    public String buildDownloadUrl(String objectKey) {
        return objectStorageService.buildPresignedUrl(objectKey, SHORT_TERM_VALIDITY.getValidity());
    }

    @Override
    public String buildAiImageInput(String objectKey) {
        return objectStorageService.buildAiPresignedUrl(objectKey, SHORT_TERM_VALIDITY.getValidity());
    }
}
