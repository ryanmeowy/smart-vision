package com.smart.vision.core.service.search;

import com.aliyuncs.exceptions.ClientException;
import com.smart.vision.core.model.dto.StsTokenDTO;

/**
 * OSS Service Interface
 * Provides operations for interacting with Alibaba Cloud OSS (Object Storage Service)
 * Main functionality includes fetching STS tokens for secure temporary access
 *
 * @author Ryan
 * @since 2025/12/18
 */
public interface OssService {

    /**
     * Fetches a temporary STS (Security Token Service) token for OSS access
     *
     * This method retrieves temporary security credentials from Alibaba Cloud STS service
     * that can be used by clients to access OSS resources with limited permissions and duration
     *
     * @return StsTokenDTO containing the temporary credentials (AccessKeyId, AccessKeySecret, SecurityToken)
     * @throws ClientException if there's an error communicating with the STS service
     */
    StsTokenDTO fetchStsToken() throws ClientException;

}
