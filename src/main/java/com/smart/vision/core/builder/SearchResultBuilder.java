package com.smart.vision.core.builder;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.smart.vision.core.model.dto.ImageSearchResultDTO;
import com.smart.vision.core.model.entity.ImageDocument;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.smart.vision.core.util.ScoreUtil.mapScoreToPercentage;

@Component
public class SearchResultBuilder {
    public List<ImageSearchResultDTO> convert2Doc(SearchResponse<ImageDocument> response) {
        List<Hit<ImageDocument>> hits = Optional.ofNullable(response)
                .map(SearchResponse::hits)
                .map(HitsMetadata::hits)
                .orElse(Collections.emptyList());

        return hits.stream()
                .filter(hit -> hit.source() != null)
                .filter(hit -> hit.id() != null)
                .filter(hit -> hit.score() != null)
                .map(hit -> {
                    ImageDocument doc = hit.source();
                    doc.setId(Long.parseLong(hit.id()));
                    return ImageSearchResultDTO.builder()
                            .document(doc)
                            .score(mapScoreToPercentage(hit.score()))
                            .sortValues(hit.sort().stream().map(this::unwrapFieldValue).collect(Collectors.toList()))
                            .build();
                }).collect(Collectors.toList());
    }

    private Object unwrapFieldValue(FieldValue fv) {
        if (fv == null) return null;
        if (fv.isDouble()) return fv.doubleValue();
        if (fv.isLong()) return String.valueOf(fv.longValue());
        if (fv.isString()) return fv.stringValue();
        if (fv.isBoolean()) return fv.booleanValue();
        return fv._get();
    }
}
