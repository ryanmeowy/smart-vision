package com.smart.vision.core.model.enums;

import lombok.Getter;

/**
 * Preset prompt templates for image-to-text styles
 *
 * @author Ryan
 * @since 2025/12/23
 */
@Getter
public enum PromptEnum {
    XIAO_HONG_SHU("xiaohongshu", "请扮演一位小红书博主。基于这张图片，写一篇吸引人的笔记。要求：标题要带emoji，正文要分段，包含情感共鸣，最后带上5个热门tag。"),
    E_COMMERCE("ecommerce", "请扮演一位资深电商运营。基于这张商品图，写一段带货文案。要求：突出卖点、痛点，语言精炼，有购买紧迫感。"),
    MOMENT("moment", "请基于这张图片，帮我写一条发朋友圈的文案。要求：简短、文艺、略带幽默，不要太矫情。"),
    DEFAULT("default", "请详细描述这张图片的内容;"),
    NAME_GEN("name_gen", "为所附图片生成一个3-6字的中文图片名，要求简洁、达意、富有美感，直接输出名称即可。"),
    TAG_GEN("tag_gen", "请分析这张图片，提取 3-5 个核心标签，包含物体、场景、风格。 请直接返回一个 JSON 字符串数组，不要包含 Markdown 格式或其他废话。例如：[\"风景\", \"雪山\", \"日落\"]"),
    OCR("ocr", "请精确提取图中的所有文本内容，包括印刷体和清晰的手写体。请不要提取水印内容。请分析图中文本的含义，丢弃掉无意义的文本的内容，比如单个的标点符号，没有上下文的字符等。如果图中没有文本内容，请输出-1。如有有文本，直接输出文本，无需说明。")
    ;

    private final String type;
    private final String prompt;

    PromptEnum(String type, String prompt) {
        this.type = type;
        this.prompt = prompt;
    }

    public static String getPromptByType(String type) {
        for (PromptEnum value : values()) {
            if (value.getType().equals(type)) {
                return value.getPrompt();
            }
        }
        return DEFAULT.getPrompt();
    }
}
