package com.smart.vision.core.task;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.ObjectListing;
import com.smart.vision.core.config.OSSConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileDeletionTask {

    private final OSS ossClient;
    @Qualifier("OSSConfig")
    private final OSSConfig ossConfig;

    /**
     * Scheduled task to delete all files in a specified folder of a specified bucket every day at midnight
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void deleteFilesInFolder() {
        try {
            String folderPath = "temp/";
            ListObjectsRequest listObjectsRequest = new ListObjectsRequest(ossConfig.getBucketName())
                    .withPrefix(folderPath);
            ObjectListing objectListing;
            do {
                objectListing = ossClient.listObjects(listObjectsRequest);
                objectListing.getObjectSummaries().forEach(objectSummary -> {
                    String objectKey = objectSummary.getKey();
                    ossClient.deleteObject(ossConfig.getBucketName(), objectKey);
                    log.info("Deleted object: {}", objectKey);
                });
                listObjectsRequest.setMarker(objectListing.getNextMarker());
            } while (objectListing.isTruncated());
        } catch (Exception e) {
            log.error("Failed to delete files in folder: {}", e.getMessage(), e);
        }
    }
}
