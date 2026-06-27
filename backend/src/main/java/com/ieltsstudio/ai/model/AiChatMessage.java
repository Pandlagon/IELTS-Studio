package com.ieltsstudio.ai.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * OpenAI-compatible chat 消息。
 *
 * <p>{@link #content} 类型说明：</p>
 * <ul>
 *   <li>文本消息：{@code String}，对应 {@code "content": "..."}</li>
 *   <li>多模态消息：{@code List<Map<String, Object>>}，
 *       形如 {@code [{"type":"text","text":"..."},{"type":"image_url","image_url":{"url":"..."}}]}，
 *       用于 vision（image_url）。</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
public class AiChatMessage {

    /** 角色：system / user / assistant */
    private final String role;

    /**
     * 消息内容。文本为 String，多模态为 List/Map。
     * 使用 Object 以兼容 OpenAI 协议的两种 content 形态。
     */
    private final Object content;

    /** 构造纯文本消息 */
    public static AiChatMessage text(String role, String text) {
        return new AiChatMessage(role, text);
    }

    /** 构造 system 文本消息 */
    public static AiChatMessage system(String text) {
        return new AiChatMessage("system", text);
    }

    /** 构造 user 文本消息 */
    public static AiChatMessage user(String text) {
        return new AiChatMessage("user", text);
    }

    /** 构造 user 多模态消息（content 为 List/Map 结构） */
    public static AiChatMessage userMultimodal(Object content) {
        return new AiChatMessage("user", content);
    }
}
