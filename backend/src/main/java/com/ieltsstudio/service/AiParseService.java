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

    private static final java.util.regex.Pattern OPTION_LINE_PATTERN =
            java.util.regex.Pattern.compile("^([A-P])\\s{2,}(.+)$");

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
                字段：questionNumber, type, text, options（JSON对象 {"A":"完整选项文字","B":"完整选项文字",…}）, answer（"A"/"B"/…）, explanation, locatorText
                - 【重要】options 必须是 JSON 对象（不是数组），每个值必须是选项的完整描述文字，不能只写字母标识。
                - 【重要】如果一组题目共享同一个选项列表（如 Questions 9-13 从 A-P 选项中选择），每道题的 options 都必须包含完整选项列表，每个选项值写出描述全文。
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
              - 【重要】输入文本可能来自 OCR，包含乱码或噪声字符（如 "CHEER EF-d"、"NSREEHLET" 等）。请忽略这些噪声，尽力从中识别出有效的文章段落和题目。
              - 【重要】questions 数组绝不能为空。即使文本有噪声，只要能识别出"Questions"、"TRUE/FALSE/NOT GIVEN"、选项列表（A-P）等题目标记，就必须提取并输出所有题目。
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
        fixLetterOnlyMcqOptions(parsed, input);
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
              - WRITING：写作任务的【完整原文】（每个任务一个条目），必须逐字包含所有考试指令，例如 "WRITING TASK 2"、"You should spend about 40 minutes on this task."、"Write about the following topic:"、"Write at least 250 words." 等。不要省略任何原文中的文字。
              - LISTENING：听力 transcript；若没有则为空数组

            "questions"：问题对象数组，必须使用以下固定结构之一：
              type "tfng"  → {questionNumber, type, text, answer("TRUE"/"FALSE"/"NOT GIVEN"), explanation, locatorText}
              type "mcq"   → {questionNumber, type, text, options{"A":"完整选项文字","B":"完整选项文字",...}, answer("A"/"B"/…), explanation, locatorText}
              type "fill"  → {questionNumber, type, text, answer(单词或短语), explanation, locatorText}
              type "write" → {questionNumber, type, text, taskType("Task1"/"Task2"), answer(写作思路≤60词), explanation, locatorText, wordLimit(150或250)}

            【重要】题型判定规则：
            - 如果题目要求从一个选项列表（如 A-P、A-H 等）中选择答案，必须标记为 "mcq"，并在 options 中列出所有可选项。
            - options 必须是 JSON 对象格式，每个选项值必须是完整的选项描述文字（不能只写字母标识）。
              例如：{"A":"Rainforests are in danger.","B":"Climate change is the main threat.","C":"..."}
            - 如果一组题目共享同一个选项列表（如 Questions 9-13 从 A-P 中选），每道题的 options 都必须包含完整选项列表。
            - 只有答案是从文章中提取的单词或短语时才使用 "fill"。
            - 选项列表可能出现在一组题目的前面或后面，需要关联到对应的题目。

            通用规则：
            - 每个分区内保留原始题号。
            - locatorText：从文章或题目中截取 3-8 个词的原文短语，不可为空。
            - answer：若有答案区则复制；否则从文章推导。
            - 输入文本可能来自 OCR，包含乱码字符，请忽略噪声，尽力识别有效内容。
            - questions 数组绝不能为空，只要能识别出题目标记就必须提取。
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
        // Post-process each section to fix letter-only MCQ options
        Object sectionsObj = result.get("sections");
        if (sectionsObj instanceof List) {
            for (Object secObj : (List<?>) sectionsObj) {
                if (secObj instanceof Map) {
                    fixLetterOnlyMcqOptions((Map<String, Object>) secObj, input);
                }
            }
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

    // ── Generic DeepSeek call helper ──────────────────────────────────────────

    private String callDeepSeek(String systemPrompt, String userMessage, int tokens, int timeoutSec) throws Exception {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", tokens);
        requestBody.put("response_format", Map.of("type", "json_object"));
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
        ));
        String requestJson = objectMapper.writeValueAsString(requestBody);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(timeoutSec))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("DeepSeek API错误 HTTP " + response.statusCode() + ": " + response.body());
        }
        JsonNode root = objectMapper.readTree(response.body());
        return root.path("choices").get(0).path("message").path("content").asText();
    }

    // ══════════════════════════════════════════════════════════════════════
    // ── Workflow: 分步解析（仅 DeepSeek）──────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════

    // Step 1: 提取文章 + 识别题组结构（不要答案和解释，输出极小）
    private static final String WORKFLOW_STEP1_PROMPT = """
            你是一个雅思（IELTS）试卷结构分析器。给定从 PDF/Word 中提取的原始文本，请完成以下任务：

            1. 提取"passages"（文章正文），字符串数组：
               - 只包含阅读文章/听力原文的正文内容，逐字保留原文（英文就输出英文）。
               - 不要包含任何题目、指令、选项。

            2. 识别"questionGroups"（题组列表），每个题组包含：
               - "range": 题号范围，如 "1-8"、"9-13"、"14"
               - "type": 题型，取值 "tfng" | "mcq" | "fill" | "write"
               - "instruction": 题组指令原文（如 "Do the following statements agree with..."、"Choose the correct responses A-P"、"Complete the notes below. Choose NO MORE THAN TWO WORDS..."）
               - "options": 若题目有共享选项列表（如 A-P），以对象形式列出 {"A":"…","B":"…",...}；若无则省略
               - "questions": 该组内每道题的原始文本数组，如 ["The plight of the rainforests has largely been ignored by the media.", "Children only accept opinions on rainforests that they encounter in their classrooms."]

            重要规则：
            - 输入文本可能含 OCR 噪声（乱码字符），请忽略噪声，尽力识别有效内容。
            - questionGroups 绝不能为空。只要文本中出现 "Questions"、编号、TRUE/FALSE/NOT GIVEN、选项列表等标记，就必须识别。
            - 题型判定：选项列表选择题（如 A-P 中选）→ "mcq"；从文章提取词语填空 → "fill"；判断题 → "tfng"；写作 → "write"。
            - 只返回 JSON 对象，不要输出 markdown 代码块或额外文字。

            输出格式：
            {
              "passages": ["文章正文..."],
              "questionGroups": [
                {"range":"1-8", "type":"tfng", "instruction":"Do the following...", "questions":["题目1原文","题目2原文",...]},
                {"range":"9-13", "type":"mcq", "instruction":"Choose the correct responses A-P", "options":{"A":"...","B":"...",...}, "questions":["题目9原文","题目10原文",...]},
                {"range":"14", "type":"mcq", "instruction":"Choose the correct letter, A, B, C, D or E", "options":{"A":"...","B":"...",...}, "questions":["题目14原文"]}
              ]
            }
            """;

    // Step 2: 给定文章和一组题目，生成每题的答案、解释、定位文本
    private static final String WORKFLOW_STEP2_PROMPT = """
            你是一个雅思（IELTS）答案解析专家。我会提供：
            1. 阅读文章（passage）
            2. 一组题目（含题号、题型、题干、可能的选项）

            请为每道题生成：
            - questionNumber: 题号（整数）
            - type: 题型（原样返回）
            - text: 题干原文（原样返回）
            - options: 选项（原样返回，若有的话）
            - answer: 正确答案
              · tfng → "TRUE"/"FALSE"/"NOT GIVEN"（或 "YES"/"NO"/"NOT GIVEN"）
              · mcq  → 选项字母如 "A"、"B" 等
              · fill → 从文章中提取的单词或短语（若有字数限制须遵守）
            - explanation: 中英混合的解题说明（2-3句，引用文章原文佐证）
            - locatorText: 从文章中截取的 3-8 个词的原文定位短语

            规则：
            - 只返回 JSON 对象 {"questions":[...]}，不要 markdown 代码块或额外文字。
            - answer 必须基于文章内容推导，不能胡编。
            - explanation 要具体引用文章原文。
            """;

    /**
     * Workflow Step 1: Extract passages and question group skeleton (no answers).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> workflowStep1(String rawText) throws Exception {
        if (!isConfigured()) throw new IllegalStateException("DeepSeek API key未配置");
        String testText = findTestContent(rawText);
        String input = testText.length() > textMaxChars
                ? testText.substring(0, textMaxChars) + "\n...[截断]"
                : testText;
        log.info("Workflow Step1: sending {} chars to DeepSeek", input.length());
        String content = callDeepSeek(WORKFLOW_STEP1_PROMPT,
                "请分析以下IELTS试题文本，提取文章和题组结构：\n\n" + input,
                4096, 90);
        log.info("Workflow Step1: response length={}", content.length());
        return objectMapper.readValue(content, Map.class);
    }

    /**
     * Workflow Step 2: Given passage and one question group, generate answers and explanations.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> workflowStep2(String passageText, Map<String, Object> questionGroup) throws Exception {
        if (!isConfigured()) throw new IllegalStateException("DeepSeek API key未配置");
        // Build a concise user message with passage + group info
        String groupJson = objectMapper.writeValueAsString(questionGroup);
        String userMsg = "【文章】\n" + passageText + "\n\n【题组信息】\n" + groupJson;

        // Limit passage to avoid token overflow
        if (userMsg.length() > textMaxChars) {
            String trimmedPassage = passageText.substring(0, Math.max(1000, textMaxChars - groupJson.length() - 200));
            userMsg = "【文章】\n" + trimmedPassage + "\n...[截断]\n\n【题组信息】\n" + groupJson;
        }

        String range = String.valueOf(questionGroup.getOrDefault("range", "?"));
        log.info("Workflow Step2: group {} – sending {} chars", range, userMsg.length());
        String content = callDeepSeek(WORKFLOW_STEP2_PROMPT, userMsg, 4096, 90);
        log.info("Workflow Step2: group {} – response length={}", range, content.length());
        return objectMapper.readValue(content, Map.class);
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

    /**
     * Post-processing: detect MCQ questions with letter-only options (e.g. ["A","B","C",...])
     * and attempt to extract full option descriptions from the raw input text.
     */
    @SuppressWarnings("unchecked")
    private void fixLetterOnlyMcqOptions(Map<String, Object> parsed, String rawText) {
        Object questionsObj = parsed.get("questions");
        if (!(questionsObj instanceof List)) return;
        List<Map<String, Object>> questions = (List<Map<String, Object>>) questionsObj;

        // Find MCQ questions with letter-only options
        List<Map<String, Object>> broken = new ArrayList<>();
        for (Map<String, Object> q : questions) {
            if (!"mcq".equals(q.get("type"))) continue;
            if (isLetterOnlyOptions(q.get("options"))) broken.add(q);
        }
        if (broken.isEmpty()) return;

        // Extract option descriptions from raw text
        Map<String, String> optionMap = extractOptionDescriptions(rawText);
        if (optionMap.size() < 3) {
            log.warn("fixLetterOnlyMcqOptions: found {} broken MCQs but could only extract {} options from raw text",
                    broken.size(), optionMap.size());
            return;
        }
        log.info("fixLetterOnlyMcqOptions: enriching {} MCQ questions with {} extracted options", broken.size(), optionMap.size());

        for (Map<String, Object> q : broken) {
            Object opts = q.get("options");
            Map<String, String> enriched = new LinkedHashMap<>();
            if (opts instanceof List) {
                for (Object o : (List<?>) opts) {
                    String letter = String.valueOf(o).trim();
                    enriched.put(letter, optionMap.getOrDefault(letter, letter));
                }
            } else if (opts instanceof Map) {
                for (Map.Entry<String, Object> e : ((Map<String, Object>) opts).entrySet()) {
                    enriched.put(e.getKey(), optionMap.getOrDefault(e.getKey(), String.valueOf(e.getValue())));
                }
            }
            q.put("options", enriched);
        }
    }

    private boolean isLetterOnlyOptions(Object opts) {
        if (opts instanceof List) {
            List<?> list = (List<?>) opts;
            return list.size() >= 3 && list.stream().allMatch(o -> {
                String s = String.valueOf(o).trim();
                return s.length() == 1 && s.charAt(0) >= 'A' && s.charAt(0) <= 'Z';
            });
        }
        if (opts instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) opts;
            return map.size() >= 3 && map.entrySet().stream().allMatch(e -> {
                String k = String.valueOf(e.getKey()).trim();
                String v = String.valueOf(e.getValue()).trim();
                return k.equals(v) && k.length() == 1;
            });
        }
        return false;
    }

    /**
     * Scan raw text for option list patterns like:
     *   A  There is a complicated combination...
     *   B  The rainforests are being destroyed...
     * Returns a map of letter -> full description text.
     */
    private Map<String, String> extractOptionDescriptions(String rawText) {
        Map<String, String> options = new LinkedHashMap<>();
        if (rawText == null) return options;

        String[] lines = rawText.split("\\n");
        String currentLetter = null;
        StringBuilder currentText = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            // Match: single letter A-P followed by 2+ spaces then text
            java.util.regex.Matcher m = OPTION_LINE_PATTERN.matcher(trimmed);
            if (m.matches()) {
                if (currentLetter != null) {
                    options.put(currentLetter, currentText.toString().trim());
                }
                currentLetter = m.group(1);
                currentText = new StringBuilder(m.group(2));
            } else if (currentLetter != null && !trimmed.isEmpty()
                    && !trimmed.matches("^\\d+\\s+.*")
                    && !trimmed.matches("^(?i)questions?\\s+.*")
                    && !trimmed.matches("^[A-P]\\s*$")) {
                // Continuation line of the current option
                currentText.append(" ").append(trimmed);
            } else if (currentLetter != null) {
                options.put(currentLetter, currentText.toString().trim());
                currentLetter = null;
            }
        }
        if (currentLetter != null) {
            options.put(currentLetter, currentText.toString().trim());
        }
        return options;
    }
}
