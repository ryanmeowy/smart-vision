package com.smart.vision.core.search.infrastructure.acl;

import com.smart.vision.core.integration.os.port.ObjectStoragePort;
import com.smart.vision.core.search.domain.port.SearchObjectStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import static com.smart.vision.core.integration.os.domain.model.PresignedValidityEnum.LONG_TERM_VALIDITY;
import static com.smart.vision.core.integration.os.domain.model.PresignedValidityEnum.MEDIUM_TERM_VALIDITY;
import static com.smart.vision.core.integration.os.domain.model.PresignedValidityEnum.SHORT_TERM_VALIDITY;

/**
 * ACL adapter from search storage port to integration OSS manager.
 */
@Component
@RequiredArgsConstructor
public class IntegrationSearchObjectStorageAcl implements SearchObjectStoragePort {

    private final ObjectStoragePort objectStorageService;

    @Override
    public String uploadFile(MultipartFile file) {
        return objectStorageService.uploadFile(file);
    }

    @Override
    public String buildAiImageInput(String objectKey, AiInputValidity validity) {
        Long effectiveValidity = switch (validity) {
            case MEDIUM -> MEDIUM_TERM_VALIDITY.getValidity();
            case SHORT -> SHORT_TERM_VALIDITY.getValidity();
        };
        return objectStorageService.buildAiPresignedUrl(objectKey, effectiveValidity);
    }

    @Override
    public String buildDisplayImageUrl(String objectKey) {
        return objectStorageService.buildPresignedUrl(objectKey, LONG_TERM_VALIDITY.getValidity());
    }
}
