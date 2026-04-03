package com.smart.vision.core.constant;

public class EmbeddingConstant {

    public static final Float CLOUD_HYBRID_SIMILARITY = 0.2f;

    public static final Float LOCAL_HYBRID_SIMILARITY = 0.4f;

    public static final Float CLOUD_SIMILAR_SIMILARITY = 0.6f;

    public static final Float LOCAL_SIMILAR_SIMILARITY = 0.8f;

    public static final Integer DEFAULT_NUM_CANDIDATES = 100;

    public static final float DEFAULT_EMBEDDING_BOOST = 0.9f;

    public static final float DEFAULT_OCR_BOOST = 0.4f;

    public static final float DEFAULT_TAG_BOOST = 0.4f;

    public static final float DEFAULT_FIELD_NAME_BOOST = 0.2f;

    public static final float DEFAULT_RELATION_S_BOOST = 0.4f;

    public static final float DEFAULT_RELATION_O_BOOST = 0.4f;

    public static final float DEFAULT_RELATION_P_BOOST = 0.05f;

    public static final int NUM_CANDIDATES_FACTOR = 8;

    public static final int DEFAULT_TOP_K = 100;

    public static final int SIMILARITY_TOP_K = 10;

    public static final int QUALITY_MIN_RESULTS = 3;

    public static final double QUALITY_RATIO_CUTOFF = 0.62d;

}
