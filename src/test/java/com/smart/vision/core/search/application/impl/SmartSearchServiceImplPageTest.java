package com.smart.vision.core.search.application.impl;

import com.smart.vision.core.search.application.support.HotSearchManager;
import com.smart.vision.core.search.application.support.SearchCursorCodec;
import com.smart.vision.core.search.application.support.SearchCursorPayload;
import com.smart.vision.core.search.application.support.SearchPageSession;
import com.smart.vision.core.search.application.support.SearchSessionManager;
import com.smart.vision.core.search.domain.port.SearchEmbeddingPort;
import com.smart.vision.core.search.domain.port.SearchObjectStoragePort;
import com.smart.vision.core.search.domain.strategy.StrategyFactory;
import com.smart.vision.core.search.infrastructure.persistence.es.repository.ImageRepository;
import com.smart.vision.core.search.interfaces.assembler.ImageDocConvertor;
import com.smart.vision.core.search.interfaces.rest.dto.SearchPageDTO;
import com.smart.vision.core.search.interfaces.rest.dto.SearchPageQueryDTO;
import com.smart.vision.core.search.interfaces.rest.dto.SearchQueryDTO;
import com.smart.vision.core.search.interfaces.rest.dto.SearchResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmartSearchServiceImplPageTest {

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

    private SmartSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SmartSearchServiceImpl(
                embeddingPort,
                imageRepository,
                imageDocConvertor,
                hotSearchManager,
                strategyFactory,
                redisTemplate,
                objectStoragePort,
                searchSessionManager,
                searchCursorCodec
        );
    }

    @Test
    void searchPage_shouldReturnFirstPage_whenNoCursor() {
        SmartSearchServiceImpl spyService = spy(service);
        List<SearchResultDTO> fullResults = mockResults(25);
        doReturn(fullResults).when(spyService).search(any(SearchQueryDTO.class));

        SearchPageSession session = new SearchPageSession(
                "session-1",
                pageFingerprint("cat", "0", 20, true, 10),
                System.currentTimeMillis() + 120_000,
                fullResults
        );
        when(searchSessionManager.create(anyString(), anyList())).thenReturn(session);
        when(searchCursorCodec.encode(any(SearchCursorPayload.class))).thenReturn("cursor-1");

        SearchPageQueryDTO query = buildQuery("cat", "0", 20, true, 10, null);
        SearchPageDTO page = spyService.searchPage(query);

        assertThat(page.getItems()).hasSize(10);
        assertThat(page.isHasMore()).isTrue();
        assertThat(page.getNextCursor()).isEqualTo("cursor-1");
        verify(spyService).search(any(SearchQueryDTO.class));
    }

    @Test
    void searchPage_shouldReturnNextPage_whenCursorProvided() {
        SmartSearchServiceImpl spyService = spy(service);
        List<SearchResultDTO> fullResults = mockResults(25);
        String fingerprint = pageFingerprint("cat", "0", 20, true, 10);
        SearchCursorPayload cursorPayload = new SearchCursorPayload(
                "session-1",
                10,
                fingerprint,
                System.currentTimeMillis() + 120_000
        );

        when(searchCursorCodec.decode("cursor-1")).thenReturn(cursorPayload);
        when(searchSessionManager.find("session-1")).thenReturn(Optional.of(new SearchPageSession(
                "session-1",
                fingerprint,
                System.currentTimeMillis() + 120_000,
                fullResults
        )));
        when(searchCursorCodec.encode(any(SearchCursorPayload.class))).thenReturn("cursor-2");

        SearchPageQueryDTO query = buildQuery("cat", "0", 20, true, 10, "cursor-1");
        SearchPageDTO page = spyService.searchPage(query);

        assertThat(page.getItems()).hasSize(10);
        assertThat(page.getItems().getFirst().getId()).isEqualTo("11");
        assertThat(page.isHasMore()).isTrue();
        assertThat(page.getNextCursor()).isEqualTo("cursor-2");
        verify(spyService, never()).search(any(SearchQueryDTO.class));
    }

    @Test
    void searchPage_shouldThrow_whenCursorFingerprintMismatch() {
        String expectedFingerprint = pageFingerprint("cat", "0", 20, true, 10);
        SearchCursorPayload payload = new SearchCursorPayload(
                "session-1",
                10,
                expectedFingerprint + "-other",
                System.currentTimeMillis() + 120_000
        );
        when(searchCursorCodec.decode("cursor-x")).thenReturn(payload);

        SearchPageQueryDTO query = buildQuery("cat", "0", 20, true, 10, "cursor-x");
        assertThatThrownBy(() -> service.searchPage(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void searchPage_shouldThrow_whenSessionExpired() {
        String fingerprint = pageFingerprint("cat", "0", 20, true, 10);
        SearchCursorPayload payload = new SearchCursorPayload(
                "session-1",
                10,
                fingerprint,
                System.currentTimeMillis() + 120_000
        );
        when(searchCursorCodec.decode("cursor-x")).thenReturn(payload);
        when(searchSessionManager.find("session-1")).thenReturn(Optional.empty());

        SearchPageQueryDTO query = buildQuery("cat", "0", 20, true, 10, "cursor-x");
        assertThatThrownBy(() -> service.searchPage(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void searchPage_shouldThrow_whenCursorPayloadExpired() {
        String fingerprint = pageFingerprint("cat", "0", 20, true, 10);
        SearchCursorPayload payload = new SearchCursorPayload(
                "session-1",
                10,
                fingerprint,
                System.currentTimeMillis() - 1
        );
        when(searchCursorCodec.decode("cursor-expired")).thenReturn(payload);

        SearchPageQueryDTO query = buildQuery("cat", "0", 20, true, 10, "cursor-expired");
        assertThatThrownBy(() -> service.searchPage(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
        verify(searchSessionManager, never()).find(anyString());
    }

    private SearchPageQueryDTO buildQuery(String keyword,
                                          String searchType,
                                          Integer topK,
                                          Boolean enableOcr,
                                          Integer limit,
                                          String cursor) {
        SearchPageQueryDTO query = new SearchPageQueryDTO();
        query.setKeyword(keyword);
        query.setSearchType(searchType);
        query.setTopK(topK);
        query.setEnableOcr(enableOcr);
        query.setLimit(limit);
        query.setCursor(cursor);
        return query;
    }

    private List<SearchResultDTO> mockResults(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> SearchResultDTO.builder()
                        .id(String.valueOf(i))
                        .score(1.0 - (i * 0.001))
                        .filename("img-" + i + ".jpg")
                        .build())
                .toList();
    }

    private String pageFingerprint(String keyword,
                                   String searchType,
                                   int topK,
                                   boolean enableOcr,
                                   int pageSize) {
        String normalized = String.join("|",
                keyword.trim().toLowerCase(),
                searchType.trim().toLowerCase(),
                String.valueOf(topK),
                String.valueOf(enableOcr),
                String.valueOf(pageSize)
        );
        return DigestUtils.md5DigestAsHex(normalized.getBytes(StandardCharsets.UTF_8));
    }
}
