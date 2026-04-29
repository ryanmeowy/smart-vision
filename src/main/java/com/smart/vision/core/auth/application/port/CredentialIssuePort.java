package com.smart.vision.core.auth.application.port;

/**
 * Auth-side outbound port for issuing temporary upload credentials.
 */
public interface CredentialIssuePort {

    IssuedCredential issueUploadCredential();

    record IssuedCredential(String accessKeyId, String accessKeySecret, String securityToken) {}
}
