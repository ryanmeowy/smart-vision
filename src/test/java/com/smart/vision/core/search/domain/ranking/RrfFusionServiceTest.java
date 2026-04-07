package com.smart.vision.core.search.domain.ranking;

import com.smart.vision.core.search.domain.model.ImageSearchResultDTO;
import com.smart.vision.core.search.infrastructure.persistence.es.document.ImageDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RrfFusionServiceTest {

    private final RrfFusionService service = new RrfFusionService();

    @Test
    void fuse_shouldPreferDocAppearingInMultipleLists() {
        List<ImageSearchResultDTO> vector = List.of(
                hit(1L, 0.95),
                hit(2L, 0.90)
        );
        List<ImageSearchResultDTO> text = List.of(
                hit(3L, 0.99),
                hit(2L, 0.45)
        );

        List<ImageSearchResultDTO> fused = service.fuse(List.of(vector, text), 3, 60);

        assertThat(fused).hasSize(3);
        assertThat(fused.getFirst().getDocument().getId()).isEqualTo(2L);
        assertThat(fused).extracting(x -> x.getDocument().getId()).containsExactly(2L, 3L, 1L);
    }

    @Test
    void fuse_shouldDeduplicateAndRespectLimit() {
        List<ImageSearchResultDTO> a = List.of(hit(1L, 0.9), hit(2L, 0.8));
        List<ImageSearchResultDTO> b = List.of(hit(2L, 0.7), hit(3L, 0.6));

        List<ImageSearchResultDTO> fused = service.fuse(List.of(a, b), 2, 60);

        assertThat(fused).hasSize(2);
        assertThat(fused).extracting(x -> x.getDocument().getId()).doesNotHaveDuplicates();
    }

    private ImageSearchResultDTO hit(Long id, double rawScore) {
        ImageDocument document = new ImageDocument();
        document.setId(id);
        document.setFileName("img-" + id + ".jpg");
        return ImageSearchResultDTO.builder()
                .document(document)
                .rawScore(rawScore)
                .score(rawScore)
                .build();
    }
}
