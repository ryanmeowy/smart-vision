package com.smart.vision.core.integration.ai.adapter.cloud;

import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.smart.vision.core.integration.ai.port.ContentGenerationService;
import com.smart.vision.core.integration.ai.client.AliyunGenManager;
import com.smart.vision.core.search.interfaces.rest.dto.GraphTripleDTO;
import com.smart.vision.core.model.enums.AliyunErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@Service
@Profile("cloud")
@RequiredArgsConstructor
public class AliyunGenImpl implements ContentGenerationService {

    private final AliyunGenManager genManager;

    @Override
    public SseEmitter streamGenerateCopy(String imageUrl, String promptType) {
        return genManager.streamGenerateCopy(imageUrl, promptType);
    }

    @Override
    public String generateSummary(String imageUrl) {
        try {
            return genManager.generateSummary(imageUrl);
        } catch (NoApiKeyException e) {
            log.error(AliyunErrorCode.API_KEY_MISSING.getMessage(), e);
        } catch (UploadFileException e) {
            log.error(AliyunErrorCode.UPLOAD_FAILED.getMessage(), e);
        } catch (Exception e) {
            log.error(AliyunErrorCode.UNKNOWN.getMessage(), e);
        }
        throw new RuntimeException("generate summary failed, try again later.");
    }

    @Override
    public String generateFileName(String imageUrl) {
        try {
            return genManager.genFileName(imageUrl);
        } catch (NoApiKeyException e) {
            log.error(AliyunErrorCode.API_KEY_MISSING.getMessage(), e);
        } catch (UploadFileException e) {
            log.error(AliyunErrorCode.UPLOAD_FAILED.getMessage(), e);
        } catch (Exception e) {
            log.error(AliyunErrorCode.UNKNOWN.getMessage(), e);
        }
        throw new RuntimeException("generate file name failed, try again later.");
    }

    @Override
    public List<String> generateTags(String imageUrl) {
        try {
            return genManager.generateTags(imageUrl);
        } catch (NoApiKeyException e) {
            log.error(AliyunErrorCode.API_KEY_MISSING.getMessage(), e);
        } catch (UploadFileException e) {
            log.error(AliyunErrorCode.UPLOAD_FAILED.getMessage(), e);
        } catch (Exception e) {
            log.error(AliyunErrorCode.UNKNOWN.getMessage(), e);
        }
        throw new RuntimeException("generate tags failed, try again later.");
    }

    /**
     * Generate graph for the image
     *
     * @param imageUrl Image URL
     * @return List of graph triples
     */
    @Override
    public List<GraphTripleDTO> generateGraph(String imageUrl) {
        try {
            return genManager.generateGraph(imageUrl);
        } catch (NoApiKeyException e) {
            log.error(AliyunErrorCode.API_KEY_MISSING.getMessage(), e);
        } catch (UploadFileException e) {
            log.error(AliyunErrorCode.UPLOAD_FAILED.getMessage(), e);
        } catch (Exception e) {
            log.error(AliyunErrorCode.UNKNOWN.getMessage(), e);
        }
        throw new RuntimeException("generate graph failed, try again later.");
    }

    @Override
    public List<GraphTripleDTO> praseTriplesFromKeyword(String keyword) {
        try {
            return genManager.praseTriplesFromKeyword(keyword);
        } catch (NoApiKeyException e) {
            log.error(AliyunErrorCode.API_KEY_MISSING.getMessage(), e);
        } catch (InputRequiredException e) {
            log.error(AliyunErrorCode.ILLEGAL_INPUT.getMessage(), e);
        } catch (Exception e) {
            log.error(AliyunErrorCode.UNKNOWN.getMessage(), e);
        }
        throw new RuntimeException("prase triples from keyword failed, try again later.");
    }
}
