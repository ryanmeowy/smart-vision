package com.smart.vision.core.constant;

/**
 * general constant clazz;
 *
 * @author Ryan
 * @since 2025/12/15
 */
public class CommonConstant {

    public static final String IMAGE_INDEX = "smart_gallery_v1";

    public static final Float HYBRID_SEARCH_DEFAULT_MIN_SCORE = 0.6f;

    public static final Integer DEFAULT_NUM_CANDIDATES = 10;

    // default pre signed url expiration date, 5 minutes
    public static final Long DEFAULT_PRESIGNED_URL_VALIDITY_TIME = 5 * 60 * 1_000L;

    public static final String ES_STORE_STAGE_FAIL = "THE-ES-STORAGE-STAGE-FAILED";

    public static final Long DEFAULT_STS_DURATION_SECONDS = 15 * 60L;
}

