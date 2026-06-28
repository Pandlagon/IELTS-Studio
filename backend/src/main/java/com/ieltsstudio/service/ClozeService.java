package com.ieltsstudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieltsstudio.ai.AiFeature;
import com.ieltsstudio.ai.AiTaskType;
import com.ieltsstudio.ai.client.OpenAiCompatibleClient;
import com.ieltsstudio.ai.model.AiChatMessage;
import com.ieltsstudio.ai.model.AiChatRequest;
import com.ieltsstudio.ai.model.AiChatResponse;
import com.ieltsstudio.ai.model.AiCredentials;
import com.ieltsstudio.ai.service.AiSettingsService;
import com.ieltsstudio.ai.service.AiUsageGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 完形填空生成 & 批改服务
 *
 * <p>Phase 5B：已接入新 AI Provider 架构，统一走
 * {@link AiSettingsService#resolve} → {@link AiUsageGuard#checkBeforeCall} →
 * {@link OpenAiCompatibleClient#chat} → {@link AiUsageGuard#markSuccess}/{@link AiUsageGuard#markFailure}。
 *
 * <ul>
 *   <li>generate — 根据用户选择的单词 + 难度，生成一篇完形填空短文</li>
 *   <li>check — 用户提交答案后，AI 批改并给出解析</li>
 * </ul>
 *
 * <p>数据全部一次性，不落库。</p>
 */
@Slf4j
@Service
public class ClozeService {

    private static final Set<String> ALLOWED_DIFFICULTIES = Set.of("easy", "medium", "hard");
    private static final int MAX_WORDS = 10;
    private static final int MAX_BLANKS = 20;
    private static final int MAX_PASSAGE_LENGTH = 6000;

    private final ObjectMapper objectMapper;
    private final AiSettingsService aiSettingsService;
    private final AiUsageGuard aiUsageGuard;
    private final OpenAiCompatibleClient openAiCompatibleClient;

    public ClozeService(ObjectMapper objectMapper,
                        AiSettingsService aiSettingsService,
                        AiUsageGuard aiUsageGuard,
                        OpenAiCompatibleClient openAiCompatibleClient) {
        this.objectMapper = objectMapper;
        this.aiSettingsService = aiSettingsService;
        this.aiUsageGuard = aiUsageGuard;
        this.openAiCompatibleClient = openAiCompatibleClient;
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
     * @param userId     当前登录用户 ID（由 Controller 从 AuthUser 注入，禁止前端传入）
     * @param words      用户选择的单词列表（1-10 个）
     * @param meanings   对应的中文释义列表（可为空或长度不足）
     * @param difficulty easy / medium / hard；非法值 fallback 为 medium
     * @return 包含 title, passage, blanks 的 Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> generate(Long userId, List<String> words, List<String> meanings, String difficulty) throws Exception {
        if (words == null || words.isEmpty()) {
            throw new IllegalArgumentException("单词列表不能为空");
        }
        if (words.size() > MAX_WORDS) {
            throw new IllegalArgumentException("最多选择 " + MAX_WORDS + " 个单词");
        }
        for (String w : words) {
            if (w == null || w.trim().isEmpty()) {
                throw new IllegalArgumentException("单词列表中包含空值");
            }
        }
        String diff = (difficulty == null || difficulty.isBlank()) ? "medium"
                : (ALLOWED_DIFFICULTIES.contains(difficulty) ? difficulty : "medium");

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
                wordListSb, diff);

        AiCredentials credentials = aiSettingsService.resolve(userId, AiTaskType.TEXT);
        String provider = providerName(credentials);
        aiUsageGuard.checkBeforeCall(userId, AiFeature.CLOZE_GENERATE, credentials.getKeyMode(), provider);
        try {
            AiChatResponse response = openAiCompatibleClient.chat(AiChatRequest.builder()
                    .credentials(credentials)
                    .messages(List.of(
                            AiChatMessage.system(GENERATE_PROMPT),
                            AiChatMessage.user(userMsg)))
                    .maxTokens(4096)
                    .temperature(0.4)
                    .jsonMode(true)
                    .timeoutSeconds(90)
                    .build());
            // 只有 provider 调用成功 + JSON 解析成功才记 markSuccess，
            // 否则解析异常会进入 catch 走 markFailure，避免一次调用同时记成功与失败。
            Map<String, Object> parsed = objectMapper.readValue(response.getContent(), Map.class);
            aiUsageGuard.markSuccess(userId, AiFeature.CLOZE_GENERATE, credentials.getKeyMode(), provider);
            return parsed;
        } catch (Exception ex) {
            aiUsageGuard.markFailure(userId, AiFeature.CLOZE_GENERATE, credentials.getKeyMode(), provider, ex);
            throw aiCallFailed(ex);
        }
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
     * @param userId      当前登录用户 ID
     * @param passage     短文全文（最长 6000 字符）
     * @param blanks      题目列表（含 number, answer, options, correctOption），最多 20 题
     * @param userAnswers 用户答案 Map: {"1": "A", "2": "C", ...}
     * @return 包含 results, score, total, summary 的 Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> check(Long userId, String passage, List<Map<String, Object>> blanks,
                                     Map<String, String> userAnswers) throws Exception {
        if (passage == null || passage.isBlank()) {
            throw new IllegalArgumentException("短文不能为空");
        }
        if (passage.length() > MAX_PASSAGE_LENGTH) {
            throw new IllegalArgumentException("短文过长（最多 " + MAX_PASSAGE_LENGTH + " 字符）");
        }
        if (blanks == null || blanks.isEmpty()) {
            throw new IllegalArgumentException("题目列表不能为空");
        }
        if (blanks.size() > MAX_BLANKS) {
            throw new IllegalArgumentException("题目数量过多（最多 " + MAX_BLANKS + " 题）");
        }
        if (userAnswers == null || userAnswers.isEmpty()) {
            throw new IllegalArgumentException("用户答案不能为空");
        }
        for (Map<String, Object> blank : blanks) {
            if (blank.get("number") == null) {
                throw new IllegalArgumentException("题目缺少 number 字段");
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("短文：\n").append(passage).append("\n\n题目与用户作答：\n");
        for (Map<String, Object> blank : blanks) {
            int num = ((Number) blank.get("number")).intValue();
            String correctAnswer = String.valueOf(blank.getOrDefault("answer", ""));
            String correctOption = String.valueOf(blank.getOrDefault("correctOption", ""));
            Map<String, String> options = blank.get("options") instanceof Map
                    ? (Map<String, String>) blank.get("options")
                    : Map.of();
            String userOpt = userAnswers.getOrDefault(String.valueOf(num), "");
            String userWord = (options != null && options.containsKey(userOpt)) ? options.get(userOpt) : userOpt;

            sb.append(String.format("第 %d 题：正确答案 = %s（%s），用户选择 = %s（%s）\n",
                    num, correctOption, correctAnswer, userOpt, userWord));
        }

        AiCredentials credentials = aiSettingsService.resolve(userId, AiTaskType.TEXT);
        String provider = providerName(credentials);
        aiUsageGuard.checkBeforeCall(userId, AiFeature.CLOZE_CHECK, credentials.getKeyMode(), provider);
        try {
            AiChatResponse response = openAiCompatibleClient.chat(AiChatRequest.builder()
                    .credentials(credentials)
                    .messages(List.of(
                            AiChatMessage.system(CHECK_PROMPT),
                            AiChatMessage.user(sb.toString())))
                    .maxTokens(4096)
                    .temperature(0.2)
                    .jsonMode(true)
                    .timeoutSeconds(90)
                    .build());
            Map<String, Object> parsed = objectMapper.readValue(response.getContent(), Map.class);
            aiUsageGuard.markSuccess(userId, AiFeature.CLOZE_CHECK, credentials.getKeyMode(), provider);
            return parsed;
        } catch (Exception ex) {
            aiUsageGuard.markFailure(userId, AiFeature.CLOZE_CHECK, credentials.getKeyMode(), provider, ex);
            throw aiCallFailed(ex);
        }
    }

    // ── Shared error helper ──────────────────────────────────────────────

    /**
     * 把 provider 调用相关异常脱敏后转为业务异常，
     * 避免把原始 response body / API Key 透传给前端。
     * 不 log ex.getMessage()，因为里面可能含 provider body。
     */
    private RuntimeException aiCallFailed(Exception ex) {
        log.warn("Cloze AI 调用失败: {}", ex.getClass().getSimpleName());
        return new RuntimeException("AI 服务暂时不可用，请稍后重试");
    }

    /** 仅返回 provider 枚举名或 null，避免在 usage record 中暴露 baseUrl / model / API Key */
    private static String providerName(AiCredentials credentials) {
        return credentials.getProvider() == null ? null : credentials.getProvider().name();
    }
}
