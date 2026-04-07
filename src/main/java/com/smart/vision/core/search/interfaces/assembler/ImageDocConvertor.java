package com.smart.vision.core.search.interfaces.assembler;

import com.google.common.collect.Lists;
import com.smart.vision.core.integration.oss.OssManager;
import com.smart.vision.core.search.domain.model.ImageSearchResultDTO;
import com.smart.vision.core.common.model.GraphTriple;
import com.smart.vision.core.search.interfaces.rest.dto.GraphTripleDTO;
import com.smart.vision.core.search.interfaces.rest.dto.SearchResultDTO;
import com.smart.vision.core.search.infrastructure.persistence.es.document.ImageDocument;
import com.smart.vision.core.integration.oss.domain.model.PresignedValidityEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
                    .relations(toDtoRelations(doc.getRelations()))
                    .build();
            resultDTOList.add(resultDTO);
        }
        return resultDTOList;
    }

    private List<GraphTripleDTO> toDtoRelations(List<GraphTriple> relations) {
        if (relations == null || relations.isEmpty()) {
            return Collections.emptyList();
        }
        return relations.stream()
                .filter(Objects::nonNull)
                .map(r -> new GraphTripleDTO(r.getS(), r.getP(), r.getO()))
                .toList();
    }
}
