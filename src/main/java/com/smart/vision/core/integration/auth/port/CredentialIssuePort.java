package com.smart.vision.core.integration.auth.port;

/**
 * Capability port for issuing temporary upload credentials.
 */
public interface CredentialIssuePort {

    IssuedCredential issueUploadCredential();

    record IssuedCredential(String accessKeyId, String accessKeySecret, String securityToken) {}
}

