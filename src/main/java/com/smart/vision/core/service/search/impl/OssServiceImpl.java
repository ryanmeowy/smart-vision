package com.smart.vision.core.service.search.impl;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.auth.sts.AssumeRoleRequest;
import com.aliyuncs.auth.sts.AssumeRoleResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.smart.vision.core.config.OssConfig;
import com.smart.vision.core.model.dto.StsTokenDTO;
import com.smart.vision.core.service.search.OssService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static com.smart.vision.core.constant.CommonConstant.DEFAULT_REGION;
import static com.smart.vision.core.constant.CommonConstant.DEFAULT_ROLE_SESSION_NAME;
import static com.smart.vision.core.constant.CommonConstant.DEFAULT_STS_DURATION_SECONDS;

/**
 * OSS Service Implementation of OssService interface that handles Alibaba Cloud OSS operations
 * This service is responsible for fetching temporary STS tokens from Alibaba Cloud
 * that can be used by clients to securely access OSS resources
 *
 * @author Ryan
 * @since 2025/12/18
 */
@Service
@RequiredArgsConstructor

public class OssServiceImpl implements OssService {

    private final OssConfig ossConfig;

    /**
     * Fetches a temporary STS (Security Token Service) token for OSS access
     * This implementation uses Alibaba Cloud STS AssumeRole API to obtain temporary
     * security credentials. The process involves:
     * 1. Creating a DefaultProfile with configured credentials
     * 2. Building an AssumeRoleRequest with role ARN and session parameters
     * 3. Calling STS service to get temporary credentials
     * 4. Extracting credentials and returning as StsTokenDTO
     *
     * @return StsTokenDTO containing the temporary credentials (AccessKeyId, AccessKeySecret, SecurityToken)
     * @throws ClientException if there's an error communicating with the STS service or invalid configuration
     */
    @Override
    public StsTokenDTO fetchStsToken() throws ClientException {
        String roleArn = ossConfig.getRoleArn();
        DefaultProfile profile = DefaultProfile.getProfile(DEFAULT_REGION, ossConfig.getAccessKeyId(), ossConfig.getAccessKeySecret());
        IAcsClient client = new DefaultAcsClient(profile);

        AssumeRoleRequest request = new AssumeRoleRequest();
        request.setRoleArn(roleArn);
        request.setRoleSessionName(DEFAULT_ROLE_SESSION_NAME);
        request.setDurationSeconds(DEFAULT_STS_DURATION_SECONDS);
        AssumeRoleResponse response = client.getAcsResponse(request);
        AssumeRoleResponse.Credentials credentials = Optional.ofNullable(response)
                .map(AssumeRoleResponse::getCredentials)
                .orElse(new AssumeRoleResponse.Credentials());
        return new StsTokenDTO(
                credentials.getAccessKeyId(),
                credentials.getAccessKeySecret(),
                credentials.getSecurityToken());
    }
}
