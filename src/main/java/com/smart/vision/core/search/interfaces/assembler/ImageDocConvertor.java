package com.smart.vision.core.search.interfaces.assembler;

import com.google.common.collect.Lists;
import com.smart.vision.core.search.domain.model.ImageSearchResultDTO;
import com.smart.vision.core.search.domain.port.SearchObjectStoragePort;
import com.smart.vision.core.search.infrastructure.persistence.es.document.ImageDocument;
import com.smart.vision.core.search.interfaces.rest.dto.SearchResultDTO;
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

    private final SearchObjectStoragePort objectStoragePort;

    public List<SearchResultDTO> convert2SearchResultDTO(List<ImageSearchResultDTO> resultList) {
        List<SearchResultDTO> resultDTOList = Lists.newArrayList();
        for (ImageSearchResultDTO result : resultList) {
            ImageDocument doc = result.getDocument();
            String presignedUrl = objectStoragePort.buildDisplayImageUrl(doc.getImagePath());
            SearchResultDTO resultDTO = SearchResultDTO.builder()
                    .score(result.getScore())
                    .url(presignedUrl)
                    .ocrText(doc.getOcrContent())
                    .id(String.valueOf(doc.getId()))
                    .filename(doc.getFileName())
                    .sortValues(result.getSortValues())
                    .highlights(result.getHighlights())
                    .tags(doc.getTags())
                    .relations(doc.getRelations())
                    .build();
            resultDTOList.add(resultDTO);
        }
        return resultDTOList;
    }
}
