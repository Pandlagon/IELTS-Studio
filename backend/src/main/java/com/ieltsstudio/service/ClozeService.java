package com.ieltsstudio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * AI 完形填空生成 & 批改服务
 *
 * <p>调用 DeepSeek API：
 * <ul>
 *   <li>generate — 根据用户选择的单词 + 难度，生成一篇完形填空短文</li>
 *   <li>check — 用户提交答案后，AI 批改并给出解析</li>
 * </ul>
 *
 * <p>数据全部一次性，不落库。
 */
@Slf4j
@Service
public class ClozeService {

    @Value("${ai.deepseek.api-key:}")
    private String apiKey;

    @Value("${ai.deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${ai.deepseek.model:deepseek-chat}")
    private String model;

    private final ObjectMapper objectMapper;

    public ClozeService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    // ── Generate ─────────────────────────────────────────────────────────

    private static final String GENERATE_PROMPT = """
            你是一位专业的英语教师，擅长制作雅思/学术英语的完形填空练习题。

            用户给出一组英语单词和难度等级，请你：
            1. 写一篇 **主题连贯** 的英语短文（150-250 词），自然地使用这些单词。
            2. 将每个目标单词替换为编号空格 __(1)__、__(2)__… 作为填空。
            3. 每个空格提供 **4 个选项**（A/B/C/D），其中一个是正确答案，其余为干扰项。
               干扰项应与正确答案词性相同或近似，具有一定迷惑性。
            4. 空格编号按出现顺序从 1 开始。

            难度说明：
            - easy：短文句式简单，干扰项差异明显
            - medium：短文使用学术语言，干扰项有一定迷惑性
            - hard：短文复杂句式多，干扰项语义接近，需要精确理解上下文

            只返回合法 JSON，不要使用 markdown 代码块，不要输出任何额外文字。

            返回格式：
            {
              "title": "短文标题",
              "passage": "短文内容（空格用 __(1)__、__(2)__ 等标记）",
              "blanks": [
                {
                  "number": 1,
                  "answer": "正确答案单词",
                  "options": {"A": "选项A", "B": "选项B", "C": "选项C", "D": "选项D"},
                  "correctOption": "A"
                }
              ]
            }
            """;

    /**
     * 调用 AI 生成完形填空。
     *
     * @param words      用户选择的单词列表（1-10 个）
     * @param meanings   对应的中文释义列表
     * @param difficulty easy / medium / hard
     * @return 包含 title, passage, blanks 的 Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> generate(List<String> words, List<String> meanings, String difficulty) throws Exception {
        if (!isConfigured()) throw new IllegalStateException("DeepSeek API key 未配置");
        if (words == null || words.isEmpty()) throw new IllegalArgumentException("单词列表不能为空");
        if (words.size() > 10) throw new IllegalArgumentException("最多选择 10 个单词");

        StringBuilder wordListSb = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            wordListSb.append(i + 1).append(". ").append(words.get(i));
            if (meanings != null && i < meanings.size() && meanings.get(i) != null) {
                wordListSb.append("（").append(meanings.get(i)).append("）");
            }
            wordListSb.append("\n");
        }

        String userMsg = String.format(
                "单词列表：\n%s\n难度：%s\n\n请根据以上单词生成完形填空练习，返回 JSON。",
                wordListSb, difficulty != null ? difficulty : "medium");

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 4096);
        requestBody.put("response_format", Map.of("type", "json_object"));
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", GENERATE_PROMPT),
                Map.of("role", "user", "content", userMsg)
        ));

        String content = callDeepSeek(requestBody);
        return objectMapper.readValue(content, Map.class);
    }

    // ── Check ────────────────────────────────────────────────────────────

    private static final String CHECK_PROMPT = """
            你是一位专业的英语教师。用户刚完成了一道完形填空练习，请你逐题批改并给出解析。

            对每一道题：
            1. 判断用户答案是否正确
            2. 给出 **简短中文解析**（1-2 句），说明为什么正确答案是对的，以及用户答案为什么错（如果错了）
            3. 在解析中适当涉及该单词的用法和搭配

            最后给出整体评价：鼓励性总结 + 需要加强的方面。

            只返回合法 JSON，不要使用 markdown 代码块，不要输出任何额外文字。

            返回格式：
            {
              "results": [
                {
                  "number": 1,
                  "correct": true,
                  "correctAnswer": "正确答案",
                  "userAnswer": "用户答案",
                  "explanation": "解析"
                }
              ],
              "score": 8,
              "total": 10,
              "summary": "整体评价"
            }
            """;

    /**
     * 批改完形填空。
     *
     * @param passage   短文全文
     * @param blanks    题目列表（含 number, answer, options, correctOption）
     * @param userAnswers 用户答案 Map: {1: "A", 2: "C", ...}
     * @return 包含 results, score, total, summary 的 Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> check(String passage, List<Map<String, Object>> blanks,
                                     Map<String, String> userAnswers) throws Exception {
        if (!isConfigured()) throw new IllegalStateException("DeepSeek API key 未配置");

        StringBuilder sb = new StringBuilder();
        sb.append("短文：\n").append(passage).append("\n\n题目与用户作答：\n");
        for (Map<String, Object> blank : blanks) {
            int num = ((Number) blank.get("number")).intValue();
            String correctAnswer = (String) blank.get("answer");
            String correctOption = (String) blank.get("correctOption");
            @SuppressWarnings("unchecked")
            Map<String, String> options = (Map<String, String>) blank.get("options");
            String userOpt = userAnswers.getOrDefault(String.valueOf(num), "");
            String userWord = options != null && options.containsKey(userOpt) ? options.get(userOpt) : userOpt;

            sb.append(String.format("第 %d 题：正确答案 = %s（%s），用户选择 = %s（%s）\n",
                    num, correctOption, correctAnswer, userOpt, userWord));
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 4096);
        requestBody.put("response_format", Map.of("type", "json_object"));
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", CHECK_PROMPT),
                Map.of("role", "user", "content", sb.toString())
        ));

        String content = callDeepSeek(requestBody);
        return objectMapper.readValue(content, Map.class);
    }

    // ── Shared AI call ───────────────────────────────────────────────────

    private String callDeepSeek(Map<String, Object> requestBody) throws Exception {
        String requestJson = objectMapper.writeValueAsString(requestBody);
        log.debug("ClozeService calling DeepSeek, request length={}", requestJson.length());

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(90))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            log.error("DeepSeek API error: HTTP {}, body={}", response.statusCode(), response.body());
            throw new RuntimeException("AI 服务调用失败 (HTTP " + response.statusCode() + ")");
        }

        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("choices").get(0)
                .path("message").path("content").asText();
        log.debug("ClozeService DeepSeek response length={}", content.length());

        // Strip markdown code fences if present
        if (content.startsWith("```")) {
            content = content.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }

        return content;
    }
}
