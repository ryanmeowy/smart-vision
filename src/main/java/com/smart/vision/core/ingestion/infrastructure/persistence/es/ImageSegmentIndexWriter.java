package com.smart.vision.core.ingestion.infrastructure.persistence.es;

import com.smart.vision.core.ingestion.infrastructure.persistence.es.document.IngestionImageDocument;
import com.smart.vision.core.search.domain.model.KbAssetTypeEnum;
import com.smart.vision.core.search.domain.model.Segment;
import com.smart.vision.core.search.domain.model.SegmentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapts image ingestion result to unified kb_segment documents.
 */
@Component
@RequiredArgsConstructor
public class ImageSegmentIndexWriter {

    private final KbSegmentBulkWriter kbSegmentBulkWriter;

    public void write(IngestionImageDocument imageDocument) {
        if (imageDocument == null || imageDocument.getId() == null) {
            return;
        }
        List<Segment> segments = toSegments(imageDocument);
        kbSegmentBulkWriter.write(segments);
    }

    private List<Segment> toSegments(IngestionImageDocument doc) {
        String assetId = String.valueOf(doc.getId());
        long createdAt = doc.getCreateTime() == null ? System.currentTimeMillis() : doc.getCreateTime();
        String title = StringUtils.hasText(doc.getRawFilename()) ? doc.getRawFilename() : doc.getFileName();
        String ocrSummary = clip(doc.getOcrContent(), 180);
        List<Segment> segments = new ArrayList<>();

        String captionText = resolveCaptionText(doc);
        if (StringUtils.hasText(captionText)) {
            segments.add(Segment.builder()
                    .segmentId(assetId + ":caption")
                    .assetId(assetId)
                    .assetType(KbAssetTypeEnum.IMAGE)
                    .segmentType(SegmentType.IMAGE_CAPTION)
                    .title(title)
                    .contentText(captionText)
                    .embedding(doc.getImageEmbedding())
                    .sourceRef(doc.getImagePath())
                    .thumbnail(doc.getImagePath())
                    .ocrSummary(ocrSummary)
                    .createdAt(createdAt)
                    .build());
        }

        if (StringUtils.hasText(doc.getOcrContent())) {
            segments.add(Segment.builder()
                    .segmentId(assetId + ":ocr:0")
                    .assetId(assetId)
                    .assetType(KbAssetTypeEnum.IMAGE)
                    .segmentType(SegmentType.IMAGE_OCR_BLOCK)
                    .title(title)
                    .ocrText(doc.getOcrContent())
                    .sourceRef(doc.getImagePath())
                    .thumbnail(doc.getImagePath())
                    .ocrSummary(ocrSummary)
                    .createdAt(createdAt)
                    .build());
        }
        return segments;
    }

    private String resolveCaptionText(IngestionImageDocument doc) {
        if (StringUtils.hasText(doc.getFileName())) {
            return doc.getFileName();
        }
        if (StringUtils.hasText(doc.getRawFilename())) {
            return doc.getRawFilename();
        }
        return null;
    }

    private String clip(String text, int maxLen) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen);
    }
}
