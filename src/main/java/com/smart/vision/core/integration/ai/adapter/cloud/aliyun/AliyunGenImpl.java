package com.smart.vision.core.integration.ai.adapter.cloud.aliyun;

import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.smart.vision.core.common.model.GraphTriple;
import com.smart.vision.core.integration.ai.port.GenPort;
import com.smart.vision.core.integration.ai.client.aliyun.AliyunGenManager;
import com.smart.vision.core.integration.ai.domain.model.AliyunErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.capability-provider", name = "gen", havingValue = "aliyun")
public class AliyunGenImpl implements GenPort {

    private final AliyunGenManager genManager;

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
    public List<GraphTriple> generateGraph(String imageUrl) {
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
    public List<GraphTriple> praseTriplesFromKeyword(String keyword) {
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
