package com.smart.vision.core.model.enums;

import lombok.Getter;

/**
 * Preset prompt templates for image-to-text styles
 *
 * @author Ryan
 * @since 2025/12/23
 */
@Getter
public enum ImageGenStyleEnum {
    XIAO_HONG_SHU("xiaohongshu", "请扮演一位小红书博主。基于这张图片，写一篇吸引人的笔记。要求：标题要带emoji，正文要分段，包含情感共鸣，最后带上5个热门tag。"),
    E_COMMERCE("ecommerce", "请扮演一位资深电商运营。基于这张商品图，写一段带货文案。要求：突出卖点、痛点，语言精炼，有购买紧迫感。"),
    MOMENT("moment", "请基于这张图片，帮我写一条发朋友圈的文案。要求：简短、文艺、略带幽默，不要太矫情。"),
    DEFAULT("default", "请详细描述这张图片的内容;")
    ;

    private final String type;
    private final String prompt;

    ImageGenStyleEnum(String type, String prompt) {
        this.type = type;
        this.prompt = prompt;
    }

    public static String getPromptByType(String type) {
        for (ImageGenStyleEnum value : values()) {
            if (value.getType().equals(type)) {
                return value.getPrompt();
            }
        }
        return DEFAULT.getPrompt();
    }
}
