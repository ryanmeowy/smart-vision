package com.smart.vision.core.integration.storage.task;

import com.smart.vision.core.integration.storage.port.ObjectStoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileDeletionTask {

    private final ObjectStoragePort objectStorageService;

    /**
     * Scheduled task to delete all files in a specified folder of a specified bucket every day at midnight
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void deleteFilesInFolder() {
        try {
            objectStorageService.deleteByFolder("temp/");
        } catch (Exception e) {
            log.error("Failed to delete files in folder: {}", e.getMessage(), e);
        }
    }
}
