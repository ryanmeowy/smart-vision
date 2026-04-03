package com.smart.vision.core.service.search.impl;

import com.smart.vision.core.ai.MultiModalEmbeddingService;
import com.smart.vision.core.convertor.ImageDocConvertor;
import com.smart.vision.core.exception.InfraException;
import com.smart.vision.core.manager.HotSearchManager;
import com.smart.vision.core.manager.OssManager;
import com.smart.vision.core.repository.ImageRepository;
import com.smart.vision.core.strategy.StrategyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmartSearchServiceImplTest {

    @Mock
    private MultiModalEmbeddingService embeddingService;
    @Mock
    private ImageRepository imageRepository;
    @Mock
    private ImageDocConvertor imageDocConvertor;
    @Mock
    private HotSearchManager hotSearchManager;
    @Mock
    private StrategyFactory strategyFactory;
    @Mock
    private RedisTemplate<String, List<Float>> redisTemplate;
    @Mock
    private OssManager ossManager;
    @Mock
    private MultipartFile multipartFile;

    private SmartSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SmartSearchServiceImpl(
                embeddingService,
                imageRepository,
                imageDocConvertor,
                hotSearchManager,
                strategyFactory,
                redisTemplate,
                ossManager
        );
    }

    @Test
    void searchByImage_shouldThrowIllegalState_whenFileReadFails() throws Exception {
        when(multipartFile.getInputStream()).thenThrow(new IOException("read failed"));

        assertThatThrownBy(() -> service.searchByImage(multipartFile, 20))
                .isInstanceOf(InfraException.class)
                .hasMessage("Image search failed, please try again later.");
    }
}
