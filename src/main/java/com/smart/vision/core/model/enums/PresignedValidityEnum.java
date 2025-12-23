package com.smart.vision.core.model.enums;

import lombok.Getter;

/**
 * Presigned URL validity
 *
 * @author Ryan
 * @since 2025/12/19
 */
@Getter
public enum PresignedValidityEnum {
    SHORT_TERM_VALIDITY(5 * 60 * 1_000L, "短期, 用于计算向量和OCR的场景"),
    MEDIUM_TERM_VALIDITY(30 * 60 * 1_000L, "中期, 暂无使用场景"),
    LONG_TERM_VALIDITY(7 * 24 * 60 * 60 * 1_000L, "长期, 用于页面展示的场景");

    PresignedValidityEnum(Long validity, String desc) {
        this.validity = validity;
        this.desc = desc;
    }

    /**
     * 有效时间, 单位: 毫秒
     */
    private final Long validity;
    private final String desc;
}
