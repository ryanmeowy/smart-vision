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

    public static final Long DEFAULT_STS_DURATION_SECONDS = 15 * 60L;

    public static final String DEFAULT_REGION = "cn-shanghai";
    //The default maximum batch size.
    public static final Integer DEFAULT_NUM_BATCH_ITEMS = 20;

    public static final String DEFAULT_ROLE_SESSION_NAME = "frontend-upload";

    public static final String TOKEN_KEY = "sys:config:upload-token";

    public static final String EMBEDDING_MODEL_NAME = "multimodal-embedding-v1";
    // Check for extremely similar images (used for deduplication)
    public static final Double DUPLICATE_THRESHOLD = 0.98;

    public static final String TAG_MODEL_NAME = "qwen-vl-plus";
}

