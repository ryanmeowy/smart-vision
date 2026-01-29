package com.smart.vision.core.constant;

public class ValidationConstant {

    public static final String AI_RESPONSE_REGEX = "\\[\\{text=([^}]+)}]";

    public static final String URL_REGEX = "^(https?|ftp)://[\\w\\-]+(\\.[\\w\\-]+)+([\\w\\-.,@?^=%&:/~+#]*[\\w\\-@?^=%&/~+#])?$";

    public static final String SINGLE_LETTER_REGEX = "^[a-zA-Z]$";

    public static final String PUNCTUATION_REGEX = "[\\pP\\pS\\p{Zs}\\p{Zl}\\p{Zp}]";

    public static final String WHITE_SPACE_REGEX = "\\s+";

    public static final String DIGIT_REGEX = "^[0-9]+$";

    public static final String MD_JSON_REGEX = "```json\\s*(\\[.*?])\\s*```";
}
