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

    public static final Float MINIMUM_SIMILARITY = 0.2f;

    public static final Float SIMILAR_QUERIES_SIMILARITY = 0.6f;
    // Candidate set size, must be greater than topK. The larger the candidate set, the higher the recall accuracy, but the slower the recall speed.
    public static final Integer DEFAULT_NUM_CANDIDATES = 10_000;

    public static final Long DEFAULT_STS_DURATION_SECONDS = 15 * 60L;

    public static final String DEFAULT_REGION = "cn-shanghai";
    //The default maximum batch size.
    public static final Integer DEFAULT_NUM_BATCH_ITEMS = 20;

    public static final String DEFAULT_ROLE_SESSION_NAME = "frontend-upload";

    public static final String TOKEN_KEY = "sys:config:upload-token";

    public static final String EMBEDDING_MODEL_NAME = "multimodal-embedding-v1";
    // Check for extremely similar images (used for deduplication)
    public static final Double DUPLICATE_THRESHOLD = 0.98;

    public static final String VISION_MODEL_NAME = "qwen-vl-plus";

    public static final String IMAGE_GEN_MODEL_NAME = "qwen-vl-max";

    public static final String HOT_SEARCH_KEY = "search:hot:ranking";

    public static final int MAX_HOT_WORDS = 10; // 返回前10个
    // Fallback data (used during cold start)
    public static final List<String>  FALLBACK_WORDS = Lists.newArrayList("森林", "大海", "猫", "赛博朋克", "发票");

    public static final List<String> MOCK_BLOCKED_WORDS = Lists.newArrayList("色情", "暴力", "血腥");

    public static final String X_OSS_PROCESS_EMBEDDING = "image/resize,l_2048,m_lfit/format,jpg/quality,q_75";

    public static final String X_OSS_PROCESS_OCR = "image/resize,l_4096,m_lfit/format,jpg";

    public static final float DEFAULT_EMBEDDING_BOOST = 0.9f;

    public static final float DEFAULT_OCR_BOOST = 0.4f;

    public static final float DEFAULT_TAG_BOOST = 0.4f;

    public static final float DEFAULT_FIELD_NAME_BOOST = 0.2f;

    public static final int NUM_CANDIDATES_FACTOR = 5;

    public static final int DEFAULT_TOP_K = 100;

    public static final int SIMILARITY_TOP_K = 10;

    public static final Integer DEFAULT_RESULT_LIMIT = 20;

    public static final String VECTOR_CACHE_PREFIX = "search:vector:";

    public static final String HASH_INDEX_PREFIX = "img:hash:";

    public static final Long SSE_TIMEOUT = 60_000L;

    public static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    public static final String AI_RESPONSE_REGEX = "\\[\\{text=([^}]+)}]";

    public static final String URL_REGEX = "^(https?|ftp)://[\\w\\-]+(\\.[\\w\\-]+)+([\\w\\-.,@?^=%&:/~+#]*[\\w\\-@?^=%&/~+#])?$";

    public static final String SINGLE_LETTER_REGEX = "^[a-zA-Z]$";

    public static final String PUNCTUATION_REGEX = "[\\pP\\pS\\p{Zs}\\p{Zl}\\p{Zp}]";

    public static final String WHITE_SPACE_REGEX = "\\s+";

    public static final String DIGIT_REGEX = "^[0-9]+$";

    public static final String TAG_REGEX = "```json\\s*(\\[.*?])\\s*```";

    public static final Integer MAX_INPUT_LENGTH = 50;
}

