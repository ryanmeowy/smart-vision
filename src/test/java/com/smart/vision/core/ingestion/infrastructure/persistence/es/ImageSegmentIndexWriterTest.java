package com.smart.vision.core.ingestion.infrastructure.persistence.es;

import com.smart.vision.core.ingestion.infrastructure.persistence.es.document.IngestionImageDocument;
import com.smart.vision.core.search.domain.model.Segment;
import com.smart.vision.core.search.domain.model.SegmentType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ImageSegmentIndexWriterTest {

    @Test
    void write_shouldBuildCaptionAndOcrSegments() {
        KbSegmentBulkWriter bulkWriter = mock(KbSegmentBulkWriter.class);
        ImageSegmentIndexWriter writer = new ImageSegmentIndexWriter(bulkWriter);

        IngestionImageDocument doc = new IngestionImageDocument();
        doc.setId(11L);
        doc.setImagePath("images/a.png");
        doc.setRawFilename("a.png");
        doc.setFileName("cat on sofa");
        doc.setOcrContent("invoice no 001");
        doc.setImageEmbedding(List.of(0.1f, 0.2f));
        doc.setCreateTime(123456L);

        writer.write(doc);

        ArgumentCaptor<List<Segment>> captor = ArgumentCaptor.forClass(List.class);
        verify(bulkWriter).write(captor.capture());
        List<Segment> segments = captor.getValue();
        assertThat(segments).hasSize(2);
        assertThat(segments).extracting(Segment::getSegmentType)
                .containsExactly(SegmentType.IMAGE_CAPTION, SegmentType.IMAGE_OCR_BLOCK);
        assertThat(segments.getFirst().getContentText()).isEqualTo("cat on sofa");
        assertThat(segments.get(1).getOcrText()).isEqualTo("invoice no 001");
    }

    @Test
    void write_shouldSkipWhenDocumentIdIsMissing() {
        KbSegmentBulkWriter bulkWriter = mock(KbSegmentBulkWriter.class);
        ImageSegmentIndexWriter writer = new ImageSegmentIndexWriter(bulkWriter);

        IngestionImageDocument doc = new IngestionImageDocument();
        doc.setId(null);
        writer.write(doc);

        verifyNoInteractions(bulkWriter);
    }
}
