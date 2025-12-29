package com.smart.vision.core.service.convert;

import com.google.common.collect.Lists;
import com.smart.vision.core.manager.OssManager;
import com.smart.vision.core.model.dto.ImageSearchResultDTO;
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

    public List<SearchResultDTO> convert2SearchResultDTO(List<ImageSearchResultDTO> resultList) {
        List<SearchResultDTO> resultDTOList = Lists.newArrayList();
        for (ImageSearchResultDTO result : resultList) {
            ImageDocument doc = result.getDocument();
            String presignedUrl = ossManager.getPresignedUrl(doc.getImagePath(), PresignedValidityEnum.LONG_TERM_VALIDITY.getValidity());
            SearchResultDTO resultDTO = SearchResultDTO.builder()
                    .score(result.getScore())
                    .url(presignedUrl)
                    .ocrText(doc.getOcrContent())
                    .id(String.valueOf(doc.getId()))
                    .filename(doc.getFileName())
                    .sortValues(result.getSortValues())
                    .tags(doc.getTags())
                    .build();
            resultDTOList.add(resultDTO);
        }
        return resultDTOList;
    }
}
