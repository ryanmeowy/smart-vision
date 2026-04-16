package com.smart.vision.core.ingestion.infrastructure.acl;

import com.smart.vision.core.ingestion.domain.port.IngestionObjectStoragePort;
import com.smart.vision.core.integration.os.port.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.smart.vision.core.integration.constant.AliyunConstant.X_OSS_PROCESS_EMBEDDING;
import static com.smart.vision.core.integration.os.domain.model.PresignedValidityEnum.SHORT_TERM_VALIDITY;

/**
 * ACL adapter from ingestion storage port to integration OSS manager.
 */
@Component
@RequiredArgsConstructor
public class OssIngestionAcl implements IngestionObjectStoragePort {

    private final ObjectStorageService objectStorageService;

    @Override
    public String buildAiImageInput(String objectKey) {
        return objectStorageService.buildAiPresignedUrl(objectKey, SHORT_TERM_VALIDITY.getValidity(), X_OSS_PROCESS_EMBEDDING);
    }
}
