package com.smart.vision.core.constant;

public class EmbeddingConstant {

    public static final Float CLOUD_HYBRID_SIMILARITY = 0.2f;

    public static final Float LOCAL_HYBRID_SIMILARITY = 0.4f;

    public static final Float CLOUD_SIMILAR_SIMILARITY = 0.6f;

    public static final Float LOCAL_SIMILAR_SIMILARITY = 0.8f;
    // Candidate set size, must be greater than topK. The larger the candidate set, the higher the recall accuracy, but the slower the recall speed.
    public static final Integer DEFAULT_NUM_CANDIDATES = 100;

    public static final float DEFAULT_EMBEDDING_BOOST = 0.9f;

    public static final float DEFAULT_OCR_BOOST = 0.4f;

    public static final float DEFAULT_TAG_BOOST = 0.4f;

    public static final float DEFAULT_FIELD_NAME_BOOST = 0.2f;

    public static final int NUM_CANDIDATES_FACTOR = 5;

    public static final int DEFAULT_TOP_K = 100;

    public static final int SIMILARITY_TOP_K = 10;

}
