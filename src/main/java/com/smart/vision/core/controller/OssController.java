package com.smart.vision.core.controller;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.auth.sts.AssumeRoleRequest;
import com.aliyuncs.auth.sts.AssumeRoleResponse;
import com.aliyuncs.profile.DefaultProfile;
import com.smart.vision.core.config.OssConfig;
import com.smart.vision.core.model.Result;
import com.smart.vision.core.model.dto.StsTokenDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.smart.vision.core.constant.CommonConstant.DEFAULT_STS_DURATION_SECONDS;

@RestController
@RequestMapping("/api/v1/oss")
@RequiredArgsConstructor
public class OssController {

    private final OssConfig ossConfig;

    @GetMapping("/sts")
    public Result<StsTokenDTO> getStsToken() {
        String roleArn = ossConfig.getRoleArn();
        DefaultProfile profile = DefaultProfile.getProfile("cn-shanghai", ossConfig.getAccessKeyId(), ossConfig.getAccessKeySecret());
        IAcsClient client = new DefaultAcsClient(profile);

        AssumeRoleRequest request = new AssumeRoleRequest();
        request.setRoleArn(roleArn);
        request.setRoleSessionName("frontend-upload");
        request.setDurationSeconds(DEFAULT_STS_DURATION_SECONDS);

        try {
            AssumeRoleResponse response = client.getAcsResponse(request);
            return Result.success(new StsTokenDTO(
                response.getCredentials().getAccessKeyId(),
                response.getCredentials().getAccessKeySecret(),
                response.getCredentials().getSecurityToken()
            ));
        } catch (Exception e) {
            return Result.error("Failed to get upload credentials");
        }
    }
}