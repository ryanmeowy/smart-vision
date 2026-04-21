package com.smart.vision.core.ingestion.infrastructure.parser;

import com.smart.vision.core.ingestion.domain.model.TextAssetMetadata;
import com.smart.vision.core.ingestion.domain.model.TextAssetType;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared parser helpers.
 */
final class TextParserSupport {

    private TextParserSupport() {
    }

    static boolean matchesExtension(TextAssetMetadata metadata, String... extensions) {
        if (metadata == null || !StringUtils.hasText(metadata.getFileName())) {
            return false;
        }
        String ext = TextAssetType.resolveExtension(metadata.getFileName());
        for (String candidate : extensions) {
            if (candidate.equalsIgnoreCase(ext)) {
                return true;
            }
        }
        return false;
    }

    static boolean matchesMimeType(TextAssetMetadata metadata, String... mimeTypes) {
        if (metadata == null || !StringUtils.hasText(metadata.getMimeType())) {
            return false;
        }
        String mimeType = metadata.getMimeType().trim().toLowerCase();
        for (String candidate : mimeTypes) {
            if (candidate.equalsIgnoreCase(mimeType)) {
                return true;
            }
        }
        return false;
    }

    static String normalizeLineEnding(String content) {
        if (content == null) {
            return "";
        }
        return content.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    static List<String> splitParagraphs(String content) {
        String normalized = normalizeLineEnding(content);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        List<String> paragraphs = new ArrayList<>();
        for (String paragraph : normalized.split("\\n\\s*\\n+")) {
            String cleaned = paragraph.trim();
            if (StringUtils.hasText(cleaned)) {
                paragraphs.add(cleaned);
            }
        }
        return paragraphs;
    }
}
