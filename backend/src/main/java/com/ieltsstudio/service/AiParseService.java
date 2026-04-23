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

@Slf4j
@Service
public class AiParseService {

    @Value("${ai.deepseek.api-key:}")
    private String apiKey;

    @Value("${ai.deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${ai.deepseek.model:deepseek-chat}")
    private String model;

    @Value("${ai.deepseek.max-tokens:4096}")
    private int maxTokens;

    @Value("${ai.deepseek.text-max-chars:12000}")
    private int textMaxChars;

    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            你是一个雅思（IELTS）试卷内容解析器。给定从任意雅思试卷（阅读 / 写作 / 听力）中提取出的原始文本，
            请解析并返回一个结构化的 JSON 对象。

            第一步 —— 判断板块类型：
            - READING：包含阅读文章（passage）以及对应的理解题
            - WRITING：包含写作任务提示（Task 1：图表/流程图等；Task 2：议论文题目）
            - LISTENING：包含与听力材料相关的问题（可能包含 transcript，也可能只有题目）

            第二步 —— 构建 JSON：

            "passages"：字符串数组
              - READING：阅读文章文本（每篇可包含标题+正文）
              - 【重要】READING 的 passages 必须尽量逐字保留原文（英文就输出英文），不要翻译、不要改写、不要总结。
              - 【重要】READING 的 passages【只能包含文章正文】。任何题目说明/题组指令（例如：
                "Questions 1-6", "Complete the notes below", "Choose NO MORE THAN TWO WORDS from the passage for each answer",
                "Write your answers in boxes ..."）都必须放在 questions 中（作为题干前缀或单独的题干说明），不要放进 passages。
              - 【重要】若文本中存在 "Answers"/"Answer Key"/"参考答案" 等答案区块：
                * passages 中不要包含该答案区块
                * questions 中的 answer 可以使用答案区块给出的答案，但不要把答案区块当作题干或正文
              - WRITING：写作任务的题干文本（每个任务一个条目）
              - LISTENING：听力 transcript；若未提供则返回空数组

            "questions"：问题对象数组。不同题型字段不同：

              ── 阅读题型 ──────────────────────────────────────────────
              type "tfng"  → True/False/Not Given 或 Yes/No/Not Given
                字段：questionNumber, type, text, answer（"TRUE"/"FALSE"/"NOT GIVEN" 或
                      "YES"/"NO"/"NOT GIVEN"）, explanation, locatorText
              type "mcq"   → 选择题（A/B/C/D 等选项）
                字段：questionNumber, type, text, options {"A":…,"B":…,…}, answer（"A"/"B"/…）, explanation, locatorText
              type "fill"  → 填空 / 简答 / 句子完成 / 匹配 / 标题题
                字段：questionNumber, type, text, answer（单词或短语）, explanation, locatorText
                - 【重要】若题组指令包含字数限制（ONE WORD ONLY / NO MORE THAN TWO WORDS 等），answer 必须严格满足该限制。

              ── 写作题型 ───────────────────────────────────────────────
              type "write" → IELTS Writing Task 1 或 Task 2
                字段：questionNumber, type,
                      text（完整题目提示，包含任何数据描述/指令文字）, 
                      taskType（"Task1" 或 "Task2"）, 
                      answer（仅输出“写作思路与要点”，不要写范文；列出需要覆盖的关键点、建议结构、以及 2-3 个应使用的关键词/短语；
                            总字数控制在 60 词以内）, 
                      explanation（Band 7+ 的评分要点提示：task achievement、coherence、vocabulary、grammar）, 
                      locatorText（题目要求中的关键短语，例如 "summarise the information"）, 
                      wordLimit（整数：Task1=150，Task2=250）

              ── 听力题型 ─────────────────────────────────────────────
              与阅读相同（tfng / mcq / fill）；locatorText：若有 transcript 则取相关原文短语，
              否则取题干中的关键词。

            通用规则：
              - 保留原始题号。
              - answer：若文本中包含答案则直接复制；否则从文章/题目推导。
              - locatorText：从文章或题目中截取 3-8 个词的“原文短语”，不可为空。
              - 如果输入明显包含阅读正文（如存在标题+段落），passages 不应为空。
              - 只返回 JSON 对象本体，不要输出 markdown 代码块，不要输出任何额外说明。

            输出示例结构：
            Reading:  {"questionNumber":1,"type":"tfng","text":"…","answer":"TRUE","explanation":"…","locatorText":"…"}
            Writing:  {"questionNumber":1,"type":"write","text":"…","taskType":"Task2","answer":"…","explanation":"…","locatorText":"…","wordLimit":250}
            Listening:{"questionNumber":1,"type":"fill","text":"…","answer":"…","explanation":"…","locatorText":"…"}
            """;

    private static final String TRANSLATE_PROMPT = """
            你是一个专业的雅思阅读中文翻译助手。
            任务：根据【整篇文章上下文】对【用户选中的英文句子/短语】进行合理、自然、准确的中文翻译。

            规则：
            - 必须输出 JSON 对象。
            - translation：给出自然、通顺的中文翻译（不要逐词硬翻）。
            - notes：可选，若原文有指代、隐含含义、学术词汇，可给 1-2 条非常简短的解释；否则给空字符串。
            - 只翻译 selectedText，不要把整篇 passage 全部翻译。
            - 若 selectedText 不是完整句子，也要结合 passage 语境补全含义后翻译。

            输入字段：
            - passage: 整篇文章（英文）
            - selectedText: 用户选中的文本（英文）
            """;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Try to skip intro/preamble pages and start from actual exam content.
     * Looks for common IELTS test-content markers; returns the original text if none found.
     */
    private String findTestContent(String rawText) {
        String[] markers = {
            // Cambridge IELTS book markers
            "READING PASSAGE 1", "Reading Passage 1", "PASSAGE 1", "Passage 1",
            "SECTION 1\n", "Section 1\n",
            "TEST 1\n", "Test 1\n", "Test One\n",
            "TASK 1\n", "Task 1\n",
            "READING TEST", "Reading Test",
            "LISTENING TEST", "Listening Test",
            "WRITING TASK 1", "Writing Task 1",
            // Common alternative formats
            "Part 1\n", "PART 1\n",
            "Section One", "Section A\n",
            "Academic Reading", "General Training Reading",
        };
        int best = Integer.MAX_VALUE;
        for (String marker : markers) {
            int idx = rawText.indexOf(marker);
            if (idx > 0 && idx < best) best = idx;
        }
        if (best != Integer.MAX_VALUE && best > 500) {
            // Start a little before the marker to include any heading above it
            int start = Math.max(0, best - 200);
            log.info("Preamble detected: skipping first {} chars, starting test content at ~{}", best, start);
            return rawText.substring(start);
        }
        return rawText;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> parseWithAi(String rawText) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("DeepSeek API key未配置，请在application.yml中设置 ai.deepseek.api-key 或环境变量 DEEPSEEK_API_KEY");
        }

        if (rawText == null || rawText.trim().length() < 80) {
            log.warn("Extracted text too short ({} chars) – likely scanned PDF, cannot parse",
                    rawText == null ? 0 : rawText.trim().length());
            throw new RuntimeException("PDF文字提取内容过少（可能是扫描版），请使用精准解析或改用Word格式上传");
        }

        String testText = findTestContent(rawText);
        String input = testText.length() > textMaxChars
                ? testText.substring(0, textMaxChars) + "\n...[截断]"
                : testText;

        log.info("Sending to DeepSeek: total {} chars, preview: [{}]",
                input.length(),
                input.substring(0, Math.min(150, input.length())).replace("\n", "↵"));

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("response_format", Map.of("type", "json_object"));
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content",
                        "请解析以下IELTS试题文本，返回结构化JSON：\n\n" + input)
        ));

        String requestJson = objectMapper.writeValueAsString(requestBody);
        log.debug("Calling DeepSeek API, input length={}", input.length());

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
            throw new RuntimeException("DeepSeek API错误 HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("choices").get(0)
                .path("message").path("content").asText();

        log.debug("DeepSeek response content length={}", content.length());

        Map<String, Object> parsed = objectMapper.readValue(content, Map.class);
        return parsed;
    }

    private static final String MULTI_SECTION_PROMPT = """
            你是一个雅思（IELTS）试卷内容解析器。以下文本可能从 PDF 中提取，
            其中可能包含 1 套或多套完整的雅思试题（例如：阅读题 1 + 阅读题 2，或剑桥真题书中的多个完整 Test）。

            第一步 —— 判断有多少个“独立试题分区”。
            当出现一套全新的阅读题/听力题/写作题开始时，视为新的分区（注意：同一套阅读题中的 Passage 2/3 不是新分区）。
            分区名称必须使用中文：
            - 阅读题1 / 阅读题2 …
            - 听力题1 / 听力题2 …
            - 写作题1 / 写作题2 …

            第二步 —— 对每个分区，按以下规则完整解析：

            "passages"：字符串数组
              - READING：阅读文章文本（每篇可包含标题+正文）
              - WRITING：写作任务题干文本（每个任务一个条目）
              - LISTENING：听力 transcript；若没有则为空数组

            "questions"：问题对象数组，必须使用以下固定结构之一：
              type "tfng"  → {questionNumber, type, text, answer("TRUE"/"FALSE"/"NOT GIVEN"), explanation, locatorText}
              type "mcq"   → {questionNumber, type, text, options{"A":…}, answer("A"/"B"/…), explanation, locatorText}
              type "fill"  → {questionNumber, type, text, answer(单词或短语), explanation, locatorText}
              type "write" → {questionNumber, type, text, taskType("Task1"/"Task2"), answer(写作思路≤60词), explanation, locatorText, wordLimit(150或250)}

            通用规则：
            - 每个分区内保留原始题号。
            - locatorText：从文章或题目中截取 3-8 个词的原文短语，不可为空。
            - answer：若有答案区则复制；否则从文章推导。
            - 只返回合法 JSON，不要使用 markdown 代码块，不要输出任何额外文字。

            返回格式：
            {"sections":[{"name":"阅读题1","type":"reading","passages":[…],"questions":[…]},…]}
            """;

    @SuppressWarnings("unchecked")
    public Map<String, Object> detectAndParseMultiSection(String rawText) throws Exception {
        if (!isConfigured()) throw new IllegalStateException("DeepSeek API key未配置");

        String input = rawText.length() > textMaxChars
                ? rawText.substring(0, textMaxChars) + "\n...[截断]"
                : rawText;

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("response_format", Map.of("type", "json_object"));
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", MULTI_SECTION_PROMPT),
                Map.of("role", "user", "content",
                        "请分析以下IELTS试题文本，检测试题分区并分别解析，返回结构化JSON：\n\n" + input)
        ));

        String requestJson = objectMapper.writeValueAsString(requestBody);
        log.debug("Calling DeepSeek multi-section API, input length={}", input.length());

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(120))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("DeepSeek multi-section API错误 HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("choices").get(0).path("message").path("content").asText();
        log.debug("DeepSeek multi-section response length={}", content.length());

        Map<String, Object> result = objectMapper.readValue(content, Map.class);
        // Normalise: if AI returned flat passages/questions instead of sections wrapper, wrap it
        if (!result.containsKey("sections") && result.containsKey("questions")) {
            Map<String, Object> section = new LinkedHashMap<>(result);
            section.putIfAbsent("name", "");
            section.putIfAbsent("type", "reading");
            result = Map.of("sections", List.of(section));
        }
        return result;
    }

    private static final String GRADE_PROMPT = """
            You are a strict but fair IELTS Writing examiner.
            Score the essay using IELTS Writing band descriptors and make the judgement evidence-based.

            Use these band principles when scoring:
            - Band 9: fully addresses the task, ideas are precise and well-developed, cohesion is natural, vocabulary is accurate and flexible, grammar is wide-ranging with rare errors.
            - Band 8: addresses the task very well with only occasional inaccuracies, organizes ideas clearly, uses a wide vocabulary and varied grammar with few errors.
            - Band 7: covers the task clearly, presents relevant main ideas, organization is generally logical, vocabulary and grammar show some range but contain noticeable errors.
            - Band 6: addresses the task adequately but unevenly, ideas may lack extension, cohesion is competent but mechanical at times, vocabulary and grammar are effective but limited or error-prone.
            - Band 5 and below: partial task response, weak organization, limited vocabulary, frequent grammar problems, or serious misunderstanding of the task.

            Scoring rules:
            - Consider whether the essay meets the required word count.
            - Do not give a high score if the essay misunderstands the task, lacks key comparisons/overview for Task 1, or has an unclear position/support for Task 2.
            - Keep scores realistic; do not inflate the band.
            - Overall band must be in 0.5 increments from 0 to 9.
            - Criterion bands must also be in 0.5 increments from 0 to 9.

            Return ONLY a JSON object with these fields:
            - band: float — overall IELTS band score
            - bandDescription: string — short Chinese summary using official-style wording such as “良好”“合格”“非常好” and a concise judgement
            - taskAchievementBand: float — task achievement/task response band
            - coherenceBand: float — coherence and cohesion band
            - vocabularyBand: float — lexical resource band
            - grammarBand: float — grammatical range and accuracy band
            - taskAchievement: string — Chinese feedback, explain task completion against descriptor language
            - coherence: string — Chinese feedback, explain logical organization, paragraphing, and cohesion
            - vocabulary: string — Chinese feedback, explain range, precision, and collocation
            - grammar: string — Chinese feedback, explain sentence variety, accuracy, and error impact
            - strengths: string — Chinese feedback, 1 sentence summarizing the strongest aspect
            - improvements: string — Chinese feedback, 2-3 concrete actions with priority order

            Writing requirements for feedback:
            - Use Chinese for all feedback text.
            - Be specific and tied to the actual essay, not generic praise.
            - Keep each dimension to 2-3 short sentences.
            - Use professional but easy-to-read wording.
            """;

    @SuppressWarnings("unchecked")
    public Map<String, Object> gradeWriting(String taskPrompt, String userEssay, int wordLimit) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("DeepSeek API key未配置");
        }
        String trimmedEssay = userEssay == null ? "" : userEssay.trim();
        int actualWords = trimmedEssay.isEmpty() ? 0 : trimmedEssay.split("\\s+").length;
        String taskType = wordLimit <= 150 ? "Task 1" : "Task 2";
        String userMsg = "题目类型\n" + taskType
                + "\n\n题目\n" + taskPrompt
                + "\n\n学生作文\n" + userEssay
                + "\n\n评分要求\n"
                + "- 要求字数: " + wordLimit + "词\n"
                + "- 实际字数: " + actualWords + "词\n"
                + "- 请严格依据 IELTS Writing 官方四项标准评分，并给出总分与分项分\n"
                + "- 若低于字数要求、偏题、论证不足、缺少 overview/比较、语法错误频繁，应明确反映在分数和反馈中";
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 1024);
        requestBody.put("response_format", Map.of("type", "json_object"));
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", GRADE_PROMPT),
                Map.of("role", "user", "content", userMsg)
        ));
        String requestJson = objectMapper.writeValueAsString(requestBody);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(60))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("DeepSeek API错误 HTTP " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("choices").get(0).path("message").path("content").asText();
        return (Map<String, Object>) objectMapper.readValue(content, Map.class);
    }

    private static final String WORD_PROMPT = """
            你是一名英语词汇专家。给定包含英文单词或短语的文本，请提取有学习价值的词汇，并返回一个 JSON 数组（词条列表）。

            规则：
            1. 只提取有意义的词汇/短语（不要提取常见功能词，例如 "the"、"is"、"and"）。
            2. 如果输入是单词/短语列表，请处理其中所有词条（最多 30 个）。
            3. 如果输入是一段文字/文章，请提取最有价值的词汇（最多 30 个）。
            4. 每个词条需包含：
               - word：单词或短语（string）
               - phonetic：IPA 音标，格式 /.../（string，使用英式发音）
               - pos：主要词性，例如 "n."、"v."、"adj."、"adv."、"phrase"（string）
               - posType：主要词性类型，取值之一："v"、"n"、"adj"、"adv"、"phrase"（string）
               - meaning：中文释义（尽量全面）
                 格式要求：
                 - 按词性分组，用 "n. " / "v. " / "adj. " 等作为前缀
                 - 每个词性组内列出 2-3 个常见义项，用 "；" 分隔
                 - 不同词性组用 " · " 分隔
                 - 重要：如果一个词常见用法包含多个词性（例如既可作名词又可作动词），必须包含所有主要词性组
                 示例：
                 seed → "n. 种子；来源；起点 · v. 播种；去籽；定为种子选手"
                 plant → "n. 植物；工厂；设备 · v. 种植；栽种；安置"
                 ability → "n. 能力；才能；本领"
                 （仅单一词性的词可以省略前缀标签）
               - example：一个自然的英文例句（string，使用双引号包裹）
            5. 只返回合法 JSON 数组，不要输出任何额外文字。
            输出示例：
            [{"word":"seed","phonetic":"/siːd/","pos":"n.","posType":"n","meaning":"n. 种子；来源；起点 · v. 播种；去籽","example":"\\"Farmers plant seeds in spring.\\""},
             {"word":"ability","phonetic":"/əˈbɪləti/","pos":"n.","posType":"n","meaning":"n. 能力；才能；本领","example":"\\"She has the ability to learn languages quickly.\\""}]
            """;

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> generateWordEntries(String inputText) throws Exception {
        if (!isConfigured()) throw new IllegalStateException("DeepSeek API key未配置");
        String truncated = inputText.length() > 3000 ? inputText.substring(0, 3000) : inputText;
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 4096);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", WORD_PROMPT),
                Map.of("role", "user", "content", truncated)
        ));
        String requestJson = objectMapper.writeValueAsString(requestBody);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(90))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) throw new RuntimeException("DeepSeek API错误 HTTP " + response.statusCode());
        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("choices").get(0).path("message").path("content").asText().trim();
        // Strip markdown code fences if present
        if (content.startsWith("```")) {
            content = content.replaceAll("^```[a-z]*\\n?", "").replaceAll("\\n?```$", "").trim();
        }
        return (List<Map<String, Object>>) objectMapper.readValue(content, List.class);
    }

    public AiParseService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> translateWithContext(String passage, String selectedText) throws Exception {
        if (!isConfigured()) throw new IllegalStateException("DeepSeek API key未配置");
        String p = passage == null ? "" : passage;
        String s = selectedText == null ? "" : selectedText.trim();
        if (s.isBlank()) throw new IllegalArgumentException("selectedText 不能为空");

        // Keep passage bounded to avoid excessive tokens
        String truncatedPassage = p.length() > 6000 ? p.substring(0, 6000) : p;

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 512);
        requestBody.put("response_format", Map.of("type", "json_object"));
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", TRANSLATE_PROMPT),
                Map.of("role", "user", "content",
                        "{" +
                                "\"passage\":" + objectMapper.writeValueAsString(truncatedPassage) + "," +
                                "\"selectedText\":" + objectMapper.writeValueAsString(s) +
                                "}")
        ));
        String requestJson = objectMapper.writeValueAsString(requestBody);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new RuntimeException("DeepSeek 翻译请求失败：HTTP " + response.statusCode() + " - " + response.body());
        }

        Map<String, Object> resp = objectMapper.readValue(response.body(), Map.class);
        Object choicesObj = resp.get("choices");
        if (!(choicesObj instanceof List) || ((List<?>) choicesObj).isEmpty()) {
            throw new RuntimeException("DeepSeek 返回为空");
        }
        Map<String, Object> choice0 = (Map<String, Object>) ((List<?>) choicesObj).get(0);
        Map<String, Object> message = (Map<String, Object>) choice0.get("message");
        String content = message != null ? Objects.toString(message.get("content"), "") : "";
        if (content.isBlank()) throw new RuntimeException("DeepSeek 翻译结果为空");
        return objectMapper.readValue(content, Map.class);
    }
}
