package com.smart.vision.core.search.infrastructure.acl;

import com.smart.vision.core.integration.oss.OssManager;
import com.smart.vision.core.search.domain.port.SearchObjectStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import static com.smart.vision.core.common.constant.AliyunConstant.X_OSS_PROCESS_EMBEDDING;
import static com.smart.vision.core.integration.oss.domain.model.PresignedValidityEnum.LONG_TERM_VALIDITY;
import static com.smart.vision.core.integration.oss.domain.model.PresignedValidityEnum.MEDIUM_TERM_VALIDITY;
import static com.smart.vision.core.integration.oss.domain.model.PresignedValidityEnum.SHORT_TERM_VALIDITY;

/**
 * ACL adapter from search storage port to integration OSS manager.
 */
@Component
@RequiredArgsConstructor
public class IntegrationSearchObjectStorageAcl implements SearchObjectStoragePort {

    private final OssManager ossManager;

    @Override
    public String uploadFile(MultipartFile file) {
        return ossManager.uploadFile(file);
    }

    @Override
    public String buildAiImageInput(String objectKey, AiInputValidity validity) {
        Long effectiveValidity = switch (validity) {
            case MEDIUM -> MEDIUM_TERM_VALIDITY.getValidity();
            case SHORT -> SHORT_TERM_VALIDITY.getValidity();
        };
        return ossManager.getAiPresignedUrl(objectKey, effectiveValidity, X_OSS_PROCESS_EMBEDDING);
    }

    @Override
    public String buildDisplayImageUrl(String objectKey) {
        return ossManager.getPresignedUrl(objectKey, LONG_TERM_VALIDITY.getValidity());
    }
}
