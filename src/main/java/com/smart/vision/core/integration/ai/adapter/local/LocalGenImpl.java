
package com.smart.vision.core.integration.ai.adapter.local;

import com.google.common.collect.Lists;
import com.smart.vision.core.integration.ai.port.ContentGenerationService;
import com.smart.vision.core.grpc.VisionProto;
import com.smart.vision.core.grpc.VisionServiceGrpc;
import com.smart.vision.core.search.interfaces.rest.dto.GraphTripleDTO;
import com.smart.vision.core.model.enums.PromptEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.grpc.StatusRuntimeException;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.smart.vision.core.constant.CommonConstant.DEFAULT_IMAGE_NAME;
import static com.smart.vision.core.constant.CommonConstant.SSE_TIMEOUT;

@Slf4j
@Service
@Profile("local")
@RequiredArgsConstructor
public class LocalGenImpl implements ContentGenerationService {

    @SuppressWarnings("unused")
    @GrpcClient("vision-python-service")
    private VisionServiceGrpc.VisionServiceBlockingStub visionStub;
    private final Executor imageGenTaskExecutor;

    @Value("${grpc.deadline.stream-caption-ms:20000}")
    private long streamCaptionDeadlineMs;
    @Value("${grpc.deadline.generate-filename-ms:3000}")
    private long generateFileNameDeadlineMs;
    @Value("${grpc.deadline.generate-tags-ms:8000}")
    private long generateTagsDeadlineMs;
    @Value("${grpc.deadline.generate-graph-ms:10000}")
    private long generateGraphDeadlineMs;
    @Value("${grpc.deadline.parse-graph-ms:8000}")
    private long parseGraphDeadlineMs;

    @Override
    public SseEmitter streamGenerateCopy(String imageUrl, String promptType) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        try {
            AtomicBoolean cancelled = new AtomicBoolean(false);
            emitter.onCompletion(() -> cancelled.set(true));
            emitter.onTimeout(() -> cancelled.set(true));

            VisionProto.GenRequest request = VisionProto.GenRequest.newBuilder()
                    .setImageUrl(imageUrl)
                    .setPrompt(PromptEnum.getPromptByType(promptType))
                    .build();
            long startNs = System.nanoTime();
            Iterator<VisionProto.StringResponse> stringResponseIterator;
            try {
                stringResponseIterator = visionStub
                        .withDeadlineAfter(streamCaptionDeadlineMs, TimeUnit.MILLISECONDS)
                        .generateCaption(request);
            } catch (StatusRuntimeException e) {
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
                log.error("gRPC generateCaption failed: statusCode={}, deadlineMs={}, elapsedMs={}", e.getStatus().getCode(), streamCaptionDeadlineMs, elapsedMs, e);
                emitter.completeWithError(e);
                return emitter;
            }

            Iterator<VisionProto.StringResponse> finalIterator = stringResponseIterator;
            imageGenTaskExecutor.execute(() -> {
                long workerStartNs = System.nanoTime();
                try {
                    while (!cancelled.get() && finalIterator.hasNext()) {
                        VisionProto.StringResponse response = finalIterator.next();
                        emitter.send(response.getContent());
                    }
                    if (!cancelled.get()) {
                        emitter.complete();
                    }
                } catch (StatusRuntimeException e) {
                    if (cancelled.get()) return;
                    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - workerStartNs);
                    log.error("gRPC generateCaption stream iteration failed: statusCode={}, deadlineMs={}, elapsedMs={}", e.getStatus().getCode(), streamCaptionDeadlineMs, elapsedMs, e);
                    emitter.completeWithError(e);
                } catch (Exception e) {
                    if (cancelled.get()) return;
                    log.error("gRPC stream generate worker failed: {}", e.getMessage(), e);
                    emitter.completeWithError(e);
                }
            });
        } catch (Exception e) {
            log.error("gRPC stream generate call failed: {}", e.getMessage(), e);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    @Override
    public String generateSummary(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) return "";
        VisionProto.GenRequest request = VisionProto.GenRequest.newBuilder()
                .setImageUrl(imageUrl)
                .setPrompt(PromptEnum.DEFAULT.getPrompt())
                .build();
        long startNs = System.nanoTime();
        try {
            Iterator<VisionProto.StringResponse> iterator = visionStub
                    .withDeadlineAfter(streamCaptionDeadlineMs, TimeUnit.MILLISECONDS)
                    .generateCaption(request);
            StringBuilder sb = new StringBuilder();
            while (iterator.hasNext()) {
                String chunk = Optional.ofNullable(iterator.next()).map(VisionProto.StringResponse::getContent).orElse("");
                if (!chunk.isBlank()) {
                    sb.append(chunk);
                }
            }
            String result = sb.toString().trim();
            if (result.isEmpty()) {
                return "";
            }
            return result.lines().collect(Collectors.joining(System.lineSeparator())).trim();
        } catch (StatusRuntimeException e) {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            log.error("gRPC generateSummary failed: statusCode={}, deadlineMs={}, elapsedMs={}", e.getStatus().getCode(), streamCaptionDeadlineMs, elapsedMs, e);
            throw new RuntimeException("generate summary failed, try again later.");
        } catch (Exception e) {
            log.error("gRPC generateSummary call failed: {}", e.getMessage(), e);
            throw new RuntimeException("generate summary failed, try again later.");
        }
    }

    @Override
    public String generateFileName(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) return DEFAULT_IMAGE_NAME;
        try {
            VisionProto.GenRequest request = VisionProto.GenRequest.newBuilder()
                    .setImageUrl(imageUrl)
                    .build();
            long startNs = System.nanoTime();
            VisionProto.GenFileNameResponse response = visionStub
                    .withDeadlineAfter(generateFileNameDeadlineMs, TimeUnit.MILLISECONDS)
                    .generateFileName(request);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            log.debug("gRPC generateFileName success: deadlineMs={}, elapsedMs={}", generateFileNameDeadlineMs, elapsedMs);
            return response.getName();
        } catch (StatusRuntimeException e) {
            log.error("gRPC generateFileName failed: statusCode={}, deadlineMs={}", e.getStatus().getCode(), generateFileNameDeadlineMs, e);
            throw new RuntimeException("generate file name failed, try again later.");
        } catch (Exception e) {
            log.error("gRPC gen name call failed: {}", e.getMessage(), e);
            throw new RuntimeException("generate file name failed, try again later.");
        }
    }


    @Override
    public List<String> generateTags(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) return Lists.newArrayList();
        try {
            VisionProto.GenRequest request = VisionProto.GenRequest.newBuilder()
                    .setImageUrl(imageUrl)
                    .build();
            VisionProto.GenTagsResponse response = visionStub
                    .withDeadlineAfter(generateTagsDeadlineMs, TimeUnit.MILLISECONDS)
                    .generateTags(request);
            return response.getTagList();
        } catch (StatusRuntimeException e) {
            log.error("gRPC generateTags failed: statusCode={}, deadlineMs={}", e.getStatus().getCode(), generateTagsDeadlineMs, e);
            throw new RuntimeException("generate tags failed, try again later.");
        } catch (Exception e) {
            log.error("gRPC gen tags call failed: {}", e.getMessage(), e);
            throw new RuntimeException("generate tags failed, try again later.");
        }
    }

    /**
     * Generate graph for the image
     *
     * @param imageUrl Image URL
     * @return List of graph triples
     */
    @Override
    public List<GraphTripleDTO> generateGraph(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) return Lists.newArrayList();
        try {
            VisionProto.GenRequest request = VisionProto.GenRequest.newBuilder()
                    .setImageUrl(imageUrl)
                    .build();
            VisionProto.GraphTriplesResponse response = visionStub
                    .withDeadlineAfter(generateGraphDeadlineMs, TimeUnit.MILLISECONDS)
                    .extractGraphTriples(request);
            List<VisionProto.GraphTriple> graphTriples = Optional.ofNullable(response).map(VisionProto.GraphTriplesResponse::getTripleList).orElseGet(Lists::newArrayList);
            return graphTriples.stream().map(x -> new GraphTripleDTO(x.getS(), x.getP(), x.getO())).toList();
        } catch (StatusRuntimeException e) {
            log.error("gRPC generateGraph failed: statusCode={}, deadlineMs={}", e.getStatus().getCode(), generateGraphDeadlineMs, e);
            throw new RuntimeException("generate graph failed, try again later.");
        } catch (Exception e) {
            log.error("gRPC gen graph call failed: {}", e.getMessage(), e);
            throw new RuntimeException("generate graph failed, try again later.");
        }
    }

    @Override
    public List<GraphTripleDTO> praseTriplesFromKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) return Lists.newArrayList();
        try {
            VisionProto.TextRequest request = VisionProto.TextRequest.newBuilder().setText(keyword).build();
            VisionProto.GraphTriplesResponse response = visionStub
                    .withDeadlineAfter(parseGraphDeadlineMs, TimeUnit.MILLISECONDS)
                    .parseQueryToGraph(request);
            List<VisionProto.GraphTriple> tripleList = response.getTripleList();
            return tripleList.stream().map(x -> new GraphTripleDTO(x.getS(), x.getP(), x.getO())).toList();
        } catch (StatusRuntimeException e) {
            log.error("gRPC parseQueryToGraph failed: statusCode={}, deadlineMs={}", e.getStatus().getCode(), parseGraphDeadlineMs, e);
            throw new RuntimeException("parse triples from keyword failed, try again later.");
        } catch (Exception e) {
            log.error("gRPC prase triples from keyword call failed: {}", e.getMessage(), e);
            throw new RuntimeException("parse triples from keyword failed, try again later.");
        }
    }
}
