package com.smart.vision.core.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * STS Token Data Transfer Object
 * Used for transferring temporary security credentials from Alibaba Cloud STS service
 *
 * @author Ryan
 * @since 2025/12/17
 */
@Data
@AllArgsConstructor
public class StsTokenDTO {

    /**
     * Access Key ID for temporary credentials
     */
    private String accessKeyId;

    /**
     * Access Key Secret for temporary credentials
     */
    private String accessKeySecret;

    /**
     * Security Token for temporary credentials
     */
    private String securityToken;
}
