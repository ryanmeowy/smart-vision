package com.smart.vision.core.constant;

import com.google.common.collect.Lists;

import java.util.List;

public class SearchConstant {

    public static final int MAX_HOT_WORDS = 10;

    public static final List<String> FALLBACK_WORDS = Lists.newArrayList("森林", "大海", "猫", "赛博朋克", "发票");

    public static final List<String> MOCK_BLOCKED_WORDS = Lists.newArrayList("色情", "暴力", "血腥");

    public static final Integer MAX_INPUT_LENGTH = 50;

    public static final Integer IMAGE_MAX_SIZE = 10 * 1024 * 1024;

    public static final Integer IMAGE_TO_IMAGE_TOP_K = 20;

    //The default maximum batch size.
    public static final Integer DEFAULT_NUM_BATCH_ITEMS = 20;

    public static final Integer DEFAULT_RESULT_LIMIT = 20;


}
