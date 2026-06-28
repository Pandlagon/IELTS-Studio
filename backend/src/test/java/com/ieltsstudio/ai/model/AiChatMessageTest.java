package com.ieltsstudio.ai.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link AiChatMessage} 单元测试。
 *
 * <p>验证静态工厂方法生成的 role 正确，且多模态 content 不被错误转为字符串。</p>
 */
class AiChatMessageTest {

    @Test
    void systemFactoryShouldCreateSystemRole() {
        AiChatMessage msg = AiChatMessage.system("you are helpful");
        assertEquals("system", msg.getRole());
        assertEquals("you are helpful", msg.getContent());
    }

    @Test
    void userStringFactoryShouldCreateUserRole() {
        AiChatMessage msg = AiChatMessage.user("hello");
        assertEquals("user", msg.getRole());
        assertEquals("hello", msg.getContent());
    }

    @Test
    void userObjectFactoryShouldPreserveMultimodalContent() {
        List<Map<String, Object>> content = List.of(
                Map.of("type", "text", "text", "describe this image"),
                Map.of("type", "image_url", "image_url", Map.of("url", "data:image/png;base64,xxx"))
        );
        AiChatMessage msg = AiChatMessage.user(content);

        assertEquals("user", msg.getRole());
        // content 应原样保留，不被转为字符串
        assertInstanceOf(List.class, msg.getContent());
        assertSame(content, msg.getContent());
    }

    @Test
    void userMultimodalFactoryShouldAlsoPreserveContent() {
        Map<String, Object> content = Map.of("type", "text", "text", "single map content");
        AiChatMessage msg = AiChatMessage.userMultimodal(content);

        assertEquals("user", msg.getRole());
        assertInstanceOf(Map.class, msg.getContent());
        assertSame(content, msg.getContent());
    }

    @Test
    void textFactoryShouldCreateGivenRole() {
        AiChatMessage msg = AiChatMessage.text("assistant", "sure, here is the answer");
        assertEquals("assistant", msg.getRole());
        assertEquals("sure, here is the answer", msg.getContent());
    }
}
