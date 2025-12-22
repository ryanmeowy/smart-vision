package com.smart.vision.core.constant;

import com.google.common.collect.Lists;

import java.util.List;

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

    public static final String HOT_SEARCH_KEY = "search:hot:ranking";

    public static final int MAX_HOT_WORDS = 10; // 返回前10个
    // Fallback data (used during cold start)
    public static final List<String>  FALLBACK_WORDS = Lists.newArrayList("森林", "大海", "猫", "赛博朋克", "发票");

    public static final List<String> MOCK_BLOCKED_WORDS = Lists.newArrayList("色情", "暴力", "血腥");

    public static final String X_OSS_PROCESS_EMBEDDING = "image/resize,l_2048,m_lfit/format,jpg/quality,q_75";

    public static final String X_OSS_PROCESS_OCR = "image/resize,l_4096,m_lfit/format,jpg";
}

