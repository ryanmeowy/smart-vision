package com.smart.vision.core.manager;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.PutObjectRequest;
import com.smart.vision.core.config.OssConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;

import static com.smart.vision.core.util.DateUtil.DATE_FORMATTER;
import static com.smart.vision.core.util.DateUtil.fetchFormatTime;

/**
 * Aliyun OSS service
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OssManager {

    private final OSS ossClient;
    private final OssConfig ossConfig;

    /**
     * Upload file
     *
     * @param file File uploaded from frontend
     * @return Relative path of the image
     */
    public String uploadFile(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null || file.isEmpty()) {
            throw new IllegalArgumentException("file cannot be empty");
        }
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String fileName = UUID.randomUUID().toString().replace("-", "") + extension;

        String folder = "images/" + fetchFormatTime(LocalDate.now(), DATE_FORMATTER);
        String path = folder + "/" + fileName;

        try {
            InputStream inputStream = file.getInputStream();
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    ossConfig.getBucketName(),
                    path,
                    inputStream
            );
            ossClient.putObject(putObjectRequest);
            return path;

        } catch (IOException e) {
            log.error("File stream read failed", e);
            throw new RuntimeException("File upload failed");
        } catch (Exception e) {
            log.error("OSS upload exception", e);
            throw new RuntimeException("OSS service exception");
        }
    }

    /**
     * [New] Generate signed temporary access URL
     *
     * @param path         File path in OSS
     * @param validityTime Validity period, unit: ms
     * @return Signed URL
     */
    public String getPresignedUrl(String path, Long validityTime) {
        Date expiration = new Date(System.currentTimeMillis() + validityTime); // 30 minutes

        // 1. Generate default URL (using configured client endpoint)
        URL url = ossClient.generatePresignedUrl(ossConfig.getBucketName(), path, expiration);

        // 2. [Fallback] If a dedicated publicEndpoint is configured, force domain replacement
        // Ensure the generated URL is accessible by AI through public network
        if (StringUtils.hasText(ossConfig.getPublicEndpoint())) {
            String originalUrl = url.toString();
            // Simple string replacement, or rebuild using URL class
            // Assume ossConfig.getEndpoint() is internal domain, replace it with public domain
            return originalUrl.replace(ossConfig.getEndpoint(), ossConfig.getPublicEndpoint());
        }

        return url.toString();
    }
}