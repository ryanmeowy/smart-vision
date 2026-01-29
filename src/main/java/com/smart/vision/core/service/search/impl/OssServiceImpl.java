package com.smart.vision.core.service.search.impl;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.auth.sts.AssumeRoleRequest;
import com.aliyuncs.auth.sts.AssumeRoleResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.google.gson.Gson;
import com.smart.vision.core.config.OSSConfig;
import com.smart.vision.core.model.dto.StsTokenDTO;
import com.smart.vision.core.service.search.OssService;
import com.smart.vision.core.util.AesUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static com.smart.vision.core.constant.AliyunConstant.DEFAULT_REGION;
import static com.smart.vision.core.constant.AliyunConstant.DEFAULT_ROLE_SESSION_NAME;
import static com.smart.vision.core.constant.AliyunConstant.DEFAULT_STS_DURATION_SECONDS;

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

    @Qualifier("OSSConfig")
    private final OSSConfig ossConfig;
    private final Gson gson;
    private final AesUtil aesUtil;

    @Override
    public String fetchStsToken() throws ClientException {
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
        StsTokenDTO stsTokenDTO = new StsTokenDTO(
                credentials.getAccessKeyId(),
                credentials.getAccessKeySecret(),
                credentials.getSecurityToken());
        String json = gson.toJson(stsTokenDTO);
        return aesUtil.encrypt(json);
    }
}
