package com.smart.vision.core.service.convert;

import com.google.common.collect.Lists;
import com.smart.vision.core.manager.OssManager;
import com.smart.vision.core.model.dto.ImageSearchResult;
import com.smart.vision.core.model.dto.SearchResultDTO;
import com.smart.vision.core.model.entity.ImageDocument;
import com.smart.vision.core.model.enums.PresignedValidityEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * image document convertor
 *
 * @author Ryan
 * @since 2025/12/15
 */
@Component
@RequiredArgsConstructor
public class ImageDocConvertor {

    private final OssManager ossManager;

    public List<SearchResultDTO> convert2SearchResultDTO(List<ImageSearchResult> resultList) {
        for (ImageSearchResult result : resultList) {
            ImageDocument doc = result.getDocument();
            String presignedUrl = ossManager.getPresignedUrl(doc.getImagePath(), PresignedValidityEnum.LONG_TERM_VALIDITY.getValidity());
            SearchResultDTO.builder()
                    .score(result.getScore())
                    .url(presignedUrl)
                    .ocrText(doc.getOcrContent())
                    .id(doc.getId())
                    .filename(doc.getFilename())
                    .build();
        }
        return Lists.newArrayList();
    }
}
