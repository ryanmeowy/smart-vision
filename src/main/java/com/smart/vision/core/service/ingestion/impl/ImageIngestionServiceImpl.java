package com.smart.vision.core.service.ingestion.impl;

import com.smart.vision.core.manager.AliyunOcrManager;
import com.smart.vision.core.manager.BailianEmbeddingManager;
import com.smart.vision.core.manager.OssManager;
import com.smart.vision.core.model.entity.ImageDocument;
import com.smart.vision.core.repository.ImageRepository;
import com.smart.vision.core.service.ingestion.ImageIngestionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Image data processing service implementation
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Service
public class ImageIngestionServiceImpl implements ImageIngestionService {
    @Resource
    private OssManager ossManager;
    @Resource
    private BailianEmbeddingManager embeddingManager;
    @Resource
    private AliyunOcrManager ocrManager;
    @Resource
    private ImageRepository imageRepository;

    public void processAndIndex(MultipartFile file) throws IOException, ExecutionException, InterruptedException {
        String url = ossManager.uploadFile(file.getInputStream(), file.getOriginalFilename());
        CompletableFuture<List<Float>> vectorFuture = CompletableFuture.supplyAsync(() -> embeddingManager.embedImage(url));
        CompletableFuture<String> ocrFuture = CompletableFuture.supplyAsync(() -> ocrManager.extractText(url));
        CompletableFuture.allOf(vectorFuture, ocrFuture).join();
        ImageDocument doc = new ImageDocument();
        doc.setUrl(url);
        doc.setImageEmbedding(vectorFuture.get());
        doc.setOcrContent(ocrFuture.get());
        imageRepository.save(doc);
    }
}
