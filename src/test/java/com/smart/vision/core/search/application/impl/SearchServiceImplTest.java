package com.smart.vision.core.search.application.impl;

import com.smart.vision.core.common.config.VectorConfig;
import com.smart.vision.core.common.exception.InfraException;
import com.smart.vision.core.search.application.support.HotSearchManager;
import com.smart.vision.core.search.application.support.SearchCursorCodec;
import com.smart.vision.core.search.application.support.SearchSessionManager;
import com.smart.vision.core.search.config.AppSearchProperties;
import com.smart.vision.core.search.domain.model.ImageSearchResultDTO;
import com.smart.vision.core.search.domain.model.StrategyTypeEnum;
import com.smart.vision.core.search.domain.port.SearchEmbeddingPort;
import com.smart.vision.core.search.domain.port.SearchObjectStoragePort;
import com.smart.vision.core.search.domain.strategy.RetrievalStrategy;
import com.smart.vision.core.search.domain.strategy.StrategyFactory;
import com.smart.vision.core.search.infrastructure.persistence.es.document.ImageDocument;
import com.smart.vision.core.search.infrastructure.persistence.es.repository.ImageRepository;
import com.smart.vision.core.search.interfaces.assembler.ImageDocConvertor;
import com.smart.vision.core.search.interfaces.rest.dto.GraphTripleDTO;
import com.smart.vision.core.search.interfaces.rest.dto.SearchExplainDTO;
import com.smart.vision.core.search.interfaces.rest.dto.SearchQueryDTO;
import com.smart.vision.core.search.interfaces.rest.dto.SearchResultDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceImplTest {

    @Mock
    private SearchEmbeddingPort embeddingPort;
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
    private SearchObjectStoragePort objectStoragePort;
    @Mock
    private SearchSessionManager searchSessionManager;
    @Mock
    private SearchCursorCodec searchCursorCodec;
    @Mock
    private VectorConfig vectorConfig;
    @Mock
    private AppSearchProperties appSearchProperties;
    @Mock
    private MultipartFile multipartFile;
    @Mock
    private ValueOperations<String, List<Float>> valueOperations;

    private SearchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SearchServiceImpl(
                embeddingPort,
                imageRepository,
                imageDocConvertor,
                hotSearchManager,
                strategyFactory,
                redisTemplate,
                objectStoragePort,
                searchSessionManager,
                searchCursorCodec,
                vectorConfig,
                appSearchProperties
        );
        ReflectionTestUtils.setField(service, "qualityAbsoluteMinScore", 0.72d);
        ReflectionTestUtils.setField(service, "aiProvider", "local");
        lenient().when(vectorConfig.getVectorProfileId()).thenReturn("test-profile");
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(service, "imageInputMode", null);
    }

    @Test
    void searchByImage_shouldThrowIllegalState_whenFileReadFails() throws Exception {
        when(multipartFile.getInputStream()).thenThrow(new IOException("read failed"));

        assertThatThrownBy(() -> service.searchByImage(multipartFile, 20))
                .isInstanceOf(InfraException.class)
                .hasMessage("Image search failed, please try again later.");
    }

    @Test
    void search_shouldAttachExplain_whenHybridHitsTextAndVector() {
        RetrievalStrategy strategy = mock(RetrievalStrategy.class);
        SearchQueryDTO query = new SearchQueryDTO();
        query.setKeyword("cat");
        query.setSearchType("0");
        query.setLimit(10);
        query.setTopK(20);
        query.setEnableOcr(true);

        ImageDocument doc = new ImageDocument();
        doc.setFileName("cat-photo.jpg");
        doc.setOcrContent("a cute cat on sofa");
        doc.setTags(List.of("cat", "home"));
        doc.setRelations(List.of(new GraphTripleDTO("cat", "on", "sofa")));
        ImageSearchResultDTO source = ImageSearchResultDTO.builder()
                .document(doc)
                .rawScore(0.95)
                .score(0.95)
                .vectorRecallHit(true)
                .textRecallHit(true)
                .build();
        SearchResultDTO dto = SearchResultDTO.builder().build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(List.of(0.1f, 0.2f));
        when(strategyFactory.getStrategy("0")).thenReturn(strategy);
        when(strategy.getType()).thenReturn(StrategyTypeEnum.HYBRID);
        when(strategy.search(eq(query), anyList())).thenReturn(List.of(source));
        when(imageDocConvertor.convert2SearchResultDTO(anyList())).thenReturn(List.of(dto));

        List<SearchResultDTO> output = service.search(query);

        assertThat(output).hasSize(1);
        SearchResultDTO result = output.getFirst();
        assertThat(result.getVectorHitStatus()).isEqualTo("VECTOR_AND_TEXT");
        assertThat(result.getExplain()).isNotNull();
        assertThat(result.getExplain().getStrategyEffective()).isEqualTo("0");
        assertThat(result.getExplain().getHitSources()).containsExactly("VECTOR");
        SearchExplainDTO.MatchedBy matchedBy = result.getExplain().getMatchedBy();
        assertThat(matchedBy.isVector()).isTrue();
        assertThat(matchedBy.isFilename()).isFalse();
        assertThat(matchedBy.isOcr()).isFalse();
        assertThat(matchedBy.isTag()).isFalse();
        assertThat(matchedBy.isGraph()).isFalse();
    }

    @Test
    void search_shouldAlignExplainWithHighlightEvidence_whenPhraseDoesNotContainExactKeyword() {
        RetrievalStrategy strategy = mock(RetrievalStrategy.class);
        SearchQueryDTO query = new SearchQueryDTO();
        query.setKeyword("趴着的猫");
        query.setSearchType("0");
        query.setLimit(10);
        query.setTopK(20);
        query.setEnableOcr(true);

        ImageDocument doc = new ImageDocument();
        doc.setFileName("cat-photo.jpg");
        doc.setOcrContent("一只猫在沙发上");
        doc.setTags(List.of("猫", "沙发"));
        ImageSearchResultDTO source = ImageSearchResultDTO.builder()
                .document(doc)
                .rawScore(0.95)
                .score(0.95)
                .highlights(Map.of("ocrContent", "一只<em>猫</em>在沙发上"))
                .highlightFields(List.of("ocrContent"))
                .vectorRecallHit(false)
                .textRecallHit(true)
                .build();
        SearchResultDTO dto = SearchResultDTO.builder().build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(List.of(0.1f, 0.2f));
        when(strategyFactory.getStrategy("0")).thenReturn(strategy);
        when(strategy.getType()).thenReturn(StrategyTypeEnum.HYBRID);
        when(strategy.search(eq(query), anyList())).thenReturn(List.of(source));
        when(imageDocConvertor.convert2SearchResultDTO(anyList())).thenReturn(List.of(dto));

        List<SearchResultDTO> output = service.search(query);

        assertThat(output).hasSize(1);
        SearchResultDTO result = output.getFirst();
        assertThat(result.getVectorHitStatus()).isEqualTo("TEXT_ONLY");
        assertThat(result.getExplain()).isNotNull();
        assertThat(result.getExplain().getHitSources()).containsExactly("OCR");
        assertThat(result.getExplain().getMatchedBy().isVector()).isFalse();
        assertThat(result.getExplain().getMatchedBy().isOcr()).isTrue();
        assertThat(result.getExplain().getMatchedBy().isTag()).isFalse();
        assertThat(result.getExplain().getMatchedBy().isFilename()).isFalse();
    }

    @Test
    void search_shouldMarkFilenameSource_whenHighlightComesFromFilename() {
        RetrievalStrategy strategy = mock(RetrievalStrategy.class);
        SearchQueryDTO query = new SearchQueryDTO();
        query.setKeyword("趴着的猫");
        query.setSearchType("0");
        query.setLimit(10);
        query.setTopK(20);
        query.setEnableOcr(true);

        ImageDocument doc = new ImageDocument();
        doc.setFileName("猫趴着.jpg");
        doc.setOcrContent("背景是沙发");
        ImageSearchResultDTO source = ImageSearchResultDTO.builder()
                .document(doc)
                .rawScore(0.93)
                .score(0.93)
                .highlights(Map.of("fileName", "<em>猫</em>趴着.jpg"))
                .highlightFields(List.of("fileName"))
                .vectorRecallHit(false)
                .textRecallHit(true)
                .build();
        SearchResultDTO dto = SearchResultDTO.builder().build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(List.of(0.1f, 0.2f));
        when(strategyFactory.getStrategy("0")).thenReturn(strategy);
        when(strategy.getType()).thenReturn(StrategyTypeEnum.HYBRID);
        when(strategy.search(eq(query), anyList())).thenReturn(List.of(source));
        when(imageDocConvertor.convert2SearchResultDTO(anyList())).thenReturn(List.of(dto));

        List<SearchResultDTO> output = service.search(query);

        assertThat(output).hasSize(1);
        SearchResultDTO result = output.getFirst();
        assertThat(result.getVectorHitStatus()).isEqualTo("TEXT_ONLY");
        assertThat(result.getExplain().getHitSources()).containsExactly("FILENAME");
        assertThat(result.getExplain().getMatchedBy().isVector()).isFalse();
        assertThat(result.getExplain().getMatchedBy().isFilename()).isTrue();
        assertThat(result.getExplain().getMatchedBy().isOcr()).isFalse();
        assertThat(result.getExplain().getMatchedBy().isTag()).isFalse();
    }

    @Test
    void search_shouldMarkVectorExplainInNativeRrfWhenTextRecallIsFalse() {
        ReflectionTestUtils.setField(service, "rrfNativeEnabled", true);
        RetrievalStrategy strategy = mock(RetrievalStrategy.class);
        SearchQueryDTO query = new SearchQueryDTO();
        query.setKeyword("cat");
        query.setSearchType("0");
        query.setLimit(10);
        query.setTopK(20);
        query.setEnableOcr(true);

        ImageDocument doc = new ImageDocument();
        doc.setFileName("photo.jpg");
        ImageSearchResultDTO source = ImageSearchResultDTO.builder()
                .document(doc)
                .rawScore(0.91)
                .score(0.91)
                .textRecallHit(false)
                .build();
        SearchResultDTO dto = SearchResultDTO.builder().build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(List.of(0.1f, 0.2f));
        when(strategyFactory.getStrategy("0")).thenReturn(strategy);
        when(strategy.getType()).thenReturn(StrategyTypeEnum.HYBRID);
        when(strategy.search(eq(query), anyList())).thenReturn(List.of(source));
        when(imageDocConvertor.convert2SearchResultDTO(anyList())).thenReturn(List.of(dto));

        List<SearchResultDTO> output = service.search(query);

        assertThat(output).hasSize(1);
        SearchResultDTO result = output.getFirst();
        assertThat(result.getVectorHitStatus()).isEqualTo("VECTOR_ONLY_LIKE");
        assertThat(result.getExplain()).isNotNull();
        assertThat(result.getExplain().getHitSources()).containsExactly("VECTOR");
        assertThat(result.getExplain().getMatchedBy().isVector()).isTrue();
    }

    @Test
    void searchByImage_shouldAttachExplain_whenVectorOnlyFlow() throws Exception {
        ReflectionTestUtils.setField(service, "imageInputMode", "bytes");
        when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream("dummy".getBytes()));
        when(multipartFile.getBytes()).thenReturn("dummy".getBytes());
        when(multipartFile.getContentType()).thenReturn("image/jpeg");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(embeddingPort.embedImage(any(byte[].class), anyString())).thenReturn(List.of(0.3f, 0.4f));

        RetrievalStrategy strategy = mock(RetrievalStrategy.class);
        when(strategyFactory.getStrategy(StrategyTypeEnum.IMAGE_TO_IMAGE.getCode())).thenReturn(strategy);
        ImageDocument doc = new ImageDocument();
        doc.setFileName("landscape.jpg");
        ImageSearchResultDTO source = ImageSearchResultDTO.builder()
                .document(doc)
                .rawScore(0.93)
                .score(0.93)
                .build();
        when(strategy.search(any(), anyList())).thenReturn(List.of(source));
        when(imageDocConvertor.convert2SearchResultDTO(anyList())).thenReturn(List.of(SearchResultDTO.builder().build()));

        List<SearchResultDTO> output = service.searchByImage(multipartFile, 5);

        assertThat(output).hasSize(1);
        SearchResultDTO result = output.getFirst();
        assertThat(result.getVectorHitStatus()).isEqualTo("VECTOR_ONLY_LIKE");
        assertThat(result.getExplain()).isNotNull();
        assertThat(result.getExplain().getStrategyEffective()).isEqualTo("3");
        assertThat(result.getExplain().getHitSources()).containsExactly("VECTOR");
        SearchExplainDTO.MatchedBy matchedBy = result.getExplain().getMatchedBy();
        assertThat(matchedBy.isVector()).isTrue();
        assertThat(matchedBy.isFilename()).isFalse();
        assertThat(matchedBy.isOcr()).isFalse();
        assertThat(matchedBy.isTag()).isFalse();
        assertThat(matchedBy.isGraph()).isFalse();
    }
}
