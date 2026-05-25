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

    private static final HttpClient SHARED_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private static final java.util.regex.Pattern OPTION_LINE_PATTERN =
            java.util.regex.Pattern.compile("^([A-Q])\\s{2,}(.+)$");

    private static final java.util.regex.Pattern INLINE_OPTION_PATTERN =
            java.util.regex.Pattern.compile("(?<![A-Za-z])([A-Q])\\s+([A-Za-z][A-Za-z\\s'-]*?)(?=\\s+[A-Q]\\s+[A-Za-z]|$)");

    private static final java.util.regex.Pattern HEADING_LINE_PATTERN =
            java.util.regex.Pattern.compile("^(i{1,3}|iv|vi{0,3}|ix|x)\\s{2,}(.+)$", java.util.regex.Pattern.CASE_INSENSITIVE);

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
              - 【极其重要】READING 的 passages 必须保留自然段落结构；自然段之间必须使用两个换行符 "\\n\\n" 分隔，不要把多个自然段合并成一个长段落。
              - 【极其重要】如果 PDF 提取文本中段落空行丢失，请根据原文版式/语义恢复自然段落边界；但不得改写、总结或重排正文句子。
              - 【极其重要】如果阅读原文中存在段落标号 A、B、C、D...，必须逐字保留这些段落标号，例如 "A International trade..."、"B What lies behind..."，不得删除、改写或替换成 Passage 1/2/3。
              - 【极其重要】同一篇阅读文章应作为 passages 数组中的一个字符串；不要把 A、B、C 段落拆成多个 passages 数组元素。
              - 【重要】READING 的 passages【只能包含文章正文】。任何题目说明/题组指令（例如：
                "Questions 1-6", "Complete the notes below", "Choose NO MORE THAN TWO WORDS from the passage for each answer",
                "Write your answers in boxes ..."）都必须放在 questions 中（作为题干前缀或单独的题干说明），不要放进 passages。
              - 【重要】若文本中存在 "Answers"/"Answer Key"/"参考答案" 等答案区块：
                * passages 中不要包含该答案区块
                * questions 中的 answer 可以使用答案区块给出的答案，但不要把答案区块当作题干或正文
              - WRITING：写作任务的【完整原文】（每个任务一个条目），必须逐字包含原文中的所有内容：
                标题（如 "WRITING TASK 2"）、时间提示（如 "You should spend about 40 minutes..."）、
                话题引导（如 "Write about the following topic:"）、题目正文、要求说明（如 "Give reasons..."）、
                字数要求（如 "Write at least 250 words."）。不得省略、摘要或改写任何原文文字。
              - LISTENING：听力 transcript；若未提供则返回空数组

            "questions"：问题对象数组。不同题型字段不同：

              ── 阅读题型 ──────────────────────────────────────────────
              type "tfng"  → True/False/Not Given 或 Yes/No/Not Given
                字段：questionNumber, type, text, answer（"TRUE"/"FALSE"/"NOT GIVEN" 或
                      "YES"/"NO"/"NOT GIVEN"）, explanation, locatorText
              type "mcq"   → 选择题（A/B/C/D 等选项）或标题匹配题（heading matching）
                字段：questionNumber, type, text, options（JSON对象 {"A":"完整选项文字","B":"完整选项文字",…}）, answer（"A"/"B"/…）, explanation, locatorText
                - 【重要】options 必须是 JSON 对象（不是数组），每个值必须是选项的完整描述文字，不能只写字母标识。
                - 【极其重要】options 绝不能写成范围字符串如 "i-vii"、"A-F" 等！必须展开为完整对象，例如 {"i":"Avoiding...","ii":"A successful..."}。
                - 【重要】对于标题匹配题（heading matching），选项 key 使用小写罗马数字 i/ii/iii/iv/v/vi/vii/viii/ix/x，值为完整标题文字。
                - 【重要】如果一组题目共享同一个选项列表（如 Questions 9-13 从 A-P 选项中选择），每道题的 options 都必须包含完整选项列表，每个选项值写出描述全文。
              type "fill"  → 填空 / 简答 / 句子完成 / 匹配 / 标题题
                字段：questionNumber, type, text, answer（单词或短语）, explanation, locatorText
                - 【重要】若题组指令包含字数限制（ONE WORD ONLY / NO MORE THAN TWO WORDS 等），answer 必须严格满足该限制。

              ── 写作题型 ───────────────────────────────────────────────
              type "write" → IELTS Writing Task 1 或 Task 2
                字段：questionNumber, type,
                      text（与 passages 内容相同，即写作任务的【完整原文】，不得省略），
                      taskType（"Task1" 或 "Task2"）, 
                      answer（仅输出“写作思路与要点”，不要写范文；列出需要覆盖的关键点、建议结构、以及 2-3 个应使用的关键词/短语；
                            总字数控制在 60 词以内）, 
                      explanation（Band 7+ 的评分要点提示：task achievement、coherence、vocabulary、grammar）, 
                      locatorText（题目要求中的关键短语，例如 "summarise the information"）, 
                      wordLimit（整数，从原文提取字数要求；若原文说 "80-100 words" 则为 80；若未明确则 Task1=150，Task2=250）

              ── 听力题型 ─────────────────────────────────────────────
              与阅读相同（tfng / mcq / fill）；locatorText：若有 transcript 则取相关原文短语，
              否则取题干中的关键词。

            通用规则：
              - 保留原始题号。
              - answer：若文本中包含答案则直接复制；否则从文章/题目推导。
              - locatorText：从输入文本中【原样复制】3-8 个连续词作为定位短语，不可为空。
              - 【极其重要】locatorText 必须是输入文本中真实存在的连续词序列，直接复制粘贴，即使有 OCR 拼写错误也保留原样。
              - 【极其重要】绝不能自己编造、改写、翻译或概括出一段看似合理但输入文本中不存在的短语。
              - 如果输入明显包含阅读正文（如存在标题+段落），passages 不应为空。
              - 【重要】输入文本可能来自 OCR，包含乱码或噪声字符（如 "CHEER EF-d"、"NSREEHLET" 等）。请忽略这些噪声，尽力从中识别出有效的文章段落和题目。
              - 【重要】questions 数组绝不能为空。即使文本有噪声，只要能识别出"Questions"、"TRUE/FALSE/NOT GIVEN"、选项列表（A-P）等题目标记，就必须提取并输出所有题目。
              - 【极其重要】如果题目指示为 "Questions 1-5" 或 "Questions 1-13"，则必须输出 questionNumber 1、2、3、4、5（或 1-13）的每一道题，不得跳过任何题号。
              - 【极其重要】对于标题匹配题（heading matching），如果文章中有 A、B、C、D、E 等标注段落，则必须为每个段落都生成一道题目（text 为 "Paragraph A"、"Paragraph B" 等），不能只生成其中一道。
              - 只返回 JSON 对象本体，不要输出 markdown 代码块，不要输出任何额外说明。

            输出示例结构：
            Reading:  {"questionNumber":1,"type":"tfng","text":"…","answer":"TRUE","explanation":"…","locatorText":"…"}
            Writing:  {"questionNumber":1,"type":"write","text":"WRITING TASK 2\nYou should spend about 40 minutes on this task.\nWrite about the following topic:\n…\nWrite at least 250 words.","taskType":"Task2","answer":"写作思路与要点","explanation":"Task Achievement: 覆盖所有论点并给出立场。Coherence: 逻辑清晰使用连接词。Vocabulary: 使用同义替换。Grammar: 句式多样。","locatorText":"factors are important in achieving","wordLimit":250}
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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(90))
                .build();

        HttpResponse<String> response = SHARED_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("DeepSeek API错误 HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("choices").get(0)
                .path("message").path("content").asText();

        log.debug("DeepSeek response content length={}", content.length());

        Map<String, Object> parsed = objectMapper.readValue(content, Map.class);
        postProcess(parsed, input);
        return parsed;
    }

    /**
     * Apply all post-processing fixes to a parsed result:
     * - Enrich letter-only MCQ options with full descriptions
     * - Enrich roman-numeral-only heading options with full descriptions
     * - Validate and correct locatorText against passages/raw text
     *
     * This is public so that AsyncParseService can call it after assembling workflow results.
     */
    public void postProcess(Map<String, Object> parsed, String rawText) {
        fixInlineStringOptions(parsed, rawText);
        fixLetterOnlyMcqOptions(parsed, rawText);
        fixRangeOptions(parsed, rawText);
        fixMissingHeadingQuestions(parsed, rawText);
        fixLocatorText(parsed, rawText);
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
              - 【极其重要】READING 的 passages 必须保留自然段落结构；自然段之间必须使用两个换行符 "\\n\\n" 分隔，不要把多个自然段合并成一个长段落。
              - 【极其重要】如果 PDF 提取文本中段落空行丢失，请根据原文版式/语义恢复自然段落边界；但不得改写、总结或重排正文句子。
              - 【极其重要】如果阅读原文中存在段落标号 A、B、C、D...，必须逐字保留这些段落标号，例如 "A International trade..."、"B What lies behind..."，不得删除、改写或替换成 Passage 1/2/3。
              - 【极其重要】同一篇阅读文章应作为 passages 数组中的一个字符串；不要把 A、B、C 段落拆成多个 passages 数组元素。
              - 只有当原文确实包含多篇独立阅读文章（例如 Reading Passage 1、Reading Passage 2、不同 Test）时，才使用多个 passages 元素。
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
            - 【极其重要】options 绝不能写成范围字符串如 "i-vii"、"A-F" 等！必须展开为完整对象。
            - 对于标题匹配题（heading matching），选项 key 使用小写罗马数字，值为完整标题文字，例如 {"i":"Avoiding...","ii":"A successful..."}。
            - 如果一组题目共享同一个选项列表（如 Questions 9-13 从 A-P 中选），每道题的 options 都必须包含完整选项列表。
            - 只有答案是从文章中提取的单词或短语时才使用 "fill"。
            - 选项列表可能出现在一组题目的前面或后面，需要关联到对应的题目。

            通用规则：
            - 每个分区内保留原始题号。
            - locatorText：从输入文本中【原样复制】3-8 个连续词作为定位短语，不可为空。
            - 【极其重要】locatorText 必须是输入文本中真实存在的连续词序列，直接复制粘贴，即使有 OCR 拼写错误也保留原样。
            - 【极其重要】绝不能自己编造、改写、翻译或概括出一段看似合理但输入文本中不存在的短语。
            - answer：若有答案区则复制；否则从文章推导。
            - 输入文本可能来自 OCR，包含乱码字符，请忽略噪声，尽力识别有效内容。
            - questions 数组绝不能为空，只要能识别出题目标记就必须提取。
            - 【极其重要】不得跳过任何题号。如果题目指示为 Questions 1-5，则必须输出 5 道题（questionNumber 1、2、3、4、5）。
            - 【极其重要】标题匹配题：如果 passage 中有标注段落 A、B、C、D、E，则必须为每个段落都生成一道题目，不能只生成其中一道。
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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(120))
                .build();

        HttpResponse<String> response = SHARED_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
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
        // Post-process each section
        Object sectionsObj = result.get("sections");
        if (sectionsObj instanceof List) {
            for (Object secObj : (List<?>) sectionsObj) {
                if (secObj instanceof Map) {
                    postProcess((Map<String, Object>) secObj, input);
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
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(60))
                .build();
        HttpResponse<String> response = SHARED_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
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
               - rootMemory：词根/词缀/拉丁或希腊词源/联想记忆法（string）
                 要求：
                 - 优先拆解真实词根、前缀、后缀，例如 "ac-/ad-（朝向、加强）+ cumulate（堆积）"
                 - 可以补充词源，例如 "源自拉丁语 cumulus（堆积）"
                 - 用中文解释该拆解如何帮助记忆
                 - 如果没有可靠词根或词源，不要编造，返回空字符串
             5. 只返回合法 JSON 数组，不要输出任何额外文字。
            输出示例：
            [{"word":"accumulate","phonetic":"/əˈkjuːmjəleɪt/","pos":"v.","posType":"v","meaning":"v. 积累；积聚；堆积","example":"\\"Dust accumulates quickly on the shelf.\\"","rootMemory":"ac-（加强）+ cumulate（堆积），源自拉丁语 cumulus（堆积），意为不断堆积，即积累。"},
             {"word":"seed","phonetic":"/siːd/","pos":"n.","posType":"n","meaning":"n. 种子；来源；起点 · v. 播种；去籽","example":"\\"Farmers plant seeds in spring.\\"","rootMemory":"seed 本义为种子，可联想到“种下来源/起点”。"},
             {"word":"ability","phonetic":"/əˈbɪləti/","pos":"n.","posType":"n","meaning":"n. 能力；才能；本领","example":"\\"She has the ability to learn languages quickly.\\"","rootMemory":"able 表示“能够”，-ity 构成名词，ability 即“能够做事的状态/能力”。"}]
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
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(90))
                .build();
        HttpResponse<String> response = SHARED_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) throw new RuntimeException("DeepSeek API错误 HTTP " + response.statusCode());
        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("choices").get(0).path("message").path("content").asText().trim();
        // Strip markdown code fences if present
        if (content.startsWith("```")) {
            content = content.replaceAll("^```[a-z]*\\n?", "").replaceAll("\\n?```$", "").trim();
        }
        List<Map<String, Object>> entries = (List<Map<String, Object>>) objectMapper.readValue(content, List.class);
        log.info("DeepSeek word generation parsed {} entries", entries.size());
        return entries;
    }

    public AiParseService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ── Generic DeepSeek call helper ──────────────────────────────────────────

    private String callDeepSeek(String systemPrompt, String userMessage, int tokens, int timeoutSec) throws Exception {
        return callDeepSeek(systemPrompt, userMessage, tokens, timeoutSec, true);
    }

    private String callDeepSeek(String systemPrompt, String userMessage, int tokens, int timeoutSec, boolean jsonMode) throws Exception {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", tokens);
        if (jsonMode) {
            requestBody.put("response_format", Map.of("type", "json_object"));
        }
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
        ));
        String requestJson = objectMapper.writeValueAsString(requestBody);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(timeoutSec))
                .build();
        HttpResponse<String> response = SHARED_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("DeepSeek API错误 HTTP " + response.statusCode() + ": " + response.body());
        }
        JsonNode root = objectMapper.readTree(response.body());
        return root.path("choices").get(0).path("message").path("content").asText();
    }

    // ── Writing guidance retry (lightweight) ─────────────────────────────────

    private static final String WRITING_GUIDANCE_PROMPT = """
            你是 IELTS 写作任务（Writing Task 1/2）的写作要点生成器。

            我会提供【写作题完整原文】（包含 WRITING TASK、时间提示、题目、要求、字数要求等）。
            你必须只返回一个 JSON 对象，字段如下：

            - taskType: "Task1" 或 "Task2"（从原文判断；出现 "TASK 1" → Task1，否则 Task2）
            - wordLimit: 整数，从原文提取字数要求；若原文说 "80-100 words" 则取 80；若未明确则 Task1=150，Task2=250
            - answer: 写作思路（中文为主，例句用英文），用换行符分段，总长 100-160 词。格式如下：
              立场：用中文概述你的观点立场，再给 1 句英文 thesis（如 "I believe that..."）
              段落骨架：
              P1 引言：中文说明引言思路 + 1 句英文 topic sentence
              P2 主体段1：中文说明论点 + 1 句英文 topic sentence
              P3 主体段2：中文说明论点 + 1 句英文 topic sentence
              P4 结论：中文说明总结方式
              高分表达：3-4 个英文短语，每个后面用中文括号说明用途
            - explanation: 评分提示（中文为主）：分别对 Task Achievement / Coherence / Vocabulary / Grammar 各给 1 句中文建议
            - locatorText: 从原文中截取 4-8 个词的关键短语（必须是原文片段，不可为空）

            规则：
            - 禁止输出 "N/A"、"Write an essay"、"Model answer not provided"、"No article" 等无用信息。
            - 只返回 JSON 对象本体，不要 markdown，不要额外说明。
            """;

    @SuppressWarnings("unchecked")
    public Map<String, Object> generateWritingGuidance(String fullWritingText) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("DeepSeek API key未配置");
        }
        if (fullWritingText == null || fullWritingText.trim().length() < 40) {
            throw new IllegalArgumentException("writing text too short");
        }
        String input = fullWritingText.trim();
        if (input.length() > 8000) {
            input = input.substring(0, 8000) + "\n...[截断]";
        }
        String content = callDeepSeek(
                WRITING_GUIDANCE_PROMPT,
                "请基于以下写作题原文生成写作要点 JSON：\n\n" + input,
                600,
                60
        );
        if (content != null && content.startsWith("```")) {
            content = content.replaceAll("^```[a-z]*\\n?", "").replaceAll("\\n?```$", "").trim();
        }
        return objectMapper.readValue(content, Map.class);
    }

    // ══════════════════════════════════════════════════════════════════════
    // ── Workflow: 分步解析（仅 DeepSeek）──────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════

    // Step 1A: 仅提取文章正文（passages），不涉及题目
    private static final String WORKFLOW_STEP1A_PROMPT = """
            你是一个雅思（IELTS）阅读文章提取器。给定从 PDF/Word 中提取的原始文本，
            请【只提取阅读文章正文】，不要提取任何题目、指令或选项。

            输出 "passages" 字符串数组，每篇独立文章一个元素。

            规则：
            - 逐字保留原文（英文就输出英文），不翻译、不改写、不总结。
            - 自然段之间用两个换行符 "\\n\\n" 分隔。
            - 如果段落空行丢失，根据语义恢复段落边界，但不得改写句子。
            - 如果原文有段落标号 A、B、C、D…，必须保留，如 "A International trade..."。
            - 同一篇文章的所有段落放在一个字符串里，不要拆分。
            - 不要包含任何题目指令（如 "Questions 1-6"、"Complete the notes below" 等）。
            - 不要包含选项列表或答案区块。
            - 遇到 "Questions"、编号列表、选项标记时立即停止提取 passage。
            - 输入文本可能含 OCR 噪声，请忽略乱码，尽力识别有效内容。
            - 只返回 JSON 对象，不要 markdown 代码块或额外文字。

            输出格式：
            {"passages": ["文章正文..."]}
            """;

    // Step 1B: 仅识别题组结构（不需要答案和解释）
    private static final String WORKFLOW_STEP1B_PROMPT = """
            你是一个雅思（IELTS）题目结构识别器。给定从 PDF/Word 中提取的原始文本，
            请识别所有题组并提取题目结构信息。不需要生成答案或解释。

            输出 "questionGroups" 数组，每个题组包含：
            - "range": 题号范围，如 "1-8"、"9-13"、"14"
            - "type": 题型，取值 "tfng" | "mcq" | "fill" | "write"
            - "instruction": 题组指令原文（如 "Do the following statements agree with..."）
            - "options": 若有共享选项列表，以对象形式列出 {"A":"完整文字","B":"完整文字",...}；若无则省略
            - "questions": 该组内每道题的原始题干文本数组

            题型判定规则：
            - 选项列表选择（heading matching、人物/地点匹配、sentence ending matching/句子结尾匹配等）→ "mcq"
            - 从文章提取词语填空，或从 A-Q 方框词库中选择单词填空 → "fill"
            - TRUE/FALSE/NOT GIVEN 或 YES/NO/NOT GIVEN → "tfng"
            - 写作任务 → "write"

            重要规则：
            - questionGroups 绝不能为空，只要文本中出现 "Questions"、编号、选项列表等标记就必须识别。
            - 不得跳过任何题号。如 "Questions 1-8" 则 questions 数组必须有 8 个元素。
            - options 必须是完整文字对象，绝不能写成 "A-F" 等范围字符串。
            - 对于 sentence ending matching（如 "Complete each sentence with the correct ending, A-G, below"），Q27-31 每道题都必须共享完整 A-G options 对象，绝不能把 options 写成单个答案字母。
            - 若 fill 题带有 A-Q 方框词库（如 A cost、B falling、C technology），该题组 type 仍为 "fill"，options 必须完整列出词库对象。
            - 对于 summary completion / notes completion / flow-chart completion 连续填空题，questions 中每个元素必须包含该空格所在的完整句子或足够上下文，绝不能只写 "14..."、"15 ......"。
              例如：Q14 text = "Research carried out by scientists ... medical problems is 14 ...."，Q15 text = "and that the speed of this change is 15 ...."。
            - 对于 heading matching，options key 用小写罗马数字（i/ii/iii/…），值为完整标题。
            - 输入文本可能含 OCR 噪声，请忽略乱码，尽力识别有效内容。
            - 只返回 JSON 对象，不要 markdown 代码块或额外文字。

            输出格式：
            {
              "questionGroups": [
                {"range":"1-8", "type":"tfng", "instruction":"Do the following...", "questions":["题目1原文","题目2原文",...]}，
                {"range":"9-13", "type":"mcq", "instruction":"Choose A-P", "options":{"A":"...","B":"...",...}, "questions":["题目9原文",...]}，
                {"range":"27-31", "type":"mcq", "instruction":"Complete each sentence with the correct ending, A-G, below.", "options":{"A":"was necessary...","B":"was necessary...","C":"was necessary..."}, "questions":["A developed system of numbering",...]}，
                {"range":"14", "type":"mcq", "instruction":"Choose A, B, C, D or E", "options":{"A":"...","B":"...",...}, "questions":["题目14原文"]}
              ]
            }
            """;

    // Step 1 (legacy combined): 提取文章 + 识别题组结构
    private static final String WORKFLOW_STEP1_PROMPT = """
            你是一个雅思（IELTS）试卷结构分析器。给定从 PDF/Word 中提取的原始文本，请完成以下任务：

            1. 提取"passages"（文章正文），字符串数组：
               - 只包含阅读文章/听力原文的正文内容，逐字保留原文（英文就输出英文）。
               - 不要包含任何题目、指令、选项。

            2. 识别"questionGroups"（题组列表），每个题组包含：
               - "range": 题号范围，如 "1-8"、"9-13"、"14"
               - "type": 题型，取值 "tfng" | "mcq" | "fill" | "write"
               - "instruction": 题组指令原文（如 "Do the following statements agree with..."、"Choose the correct responses A-P"、"Complete the notes below. Choose NO MORE THAN TWO WORDS..."）
               - "options": 若题目有共享选项列表（如 A-P 方框词库、heading list），以对象形式列出 {"A":"…","B":"…",...}；若无则省略
               - "questions": 该组内每道题的原始文本数组，如 ["The plight of the rainforests has largely been ignored by the media.", "Children only accept opinions on rainforests that they encounter in their classrooms."]

            重要规则：
            - 输入文本可能含 OCR 噪声（乱码字符），请忽略噪声，尽力识别有效内容。
            - questionGroups 绝不能为空。只要文本中出现 "Questions"、编号、TRUE/FALSE/NOT GIVEN、选项列表等标记，就必须识别。
            - 题型判定：heading/person/place/sentence ending 等匹配选择题 → "mcq"；从文章提取词语填空或 A-Q 方框词库填空 → "fill"；判断题 → "tfng"；写作 → "write"。
            - 对于 sentence ending matching（如 "Complete each sentence with the correct ending, A-G, below"），每道题都必须共享完整 A-G options 对象，绝不能把 options 写成单个答案字母。
            - 若 fill 题带有 A-Q 方框词库（如 A cost、B falling、C technology），该题组 type 仍为 "fill"，options 必须完整列出词库对象。
            - 对于 summary completion / notes completion / flow-chart completion 连续填空题，questions 中每个元素必须包含该空格所在的完整句子或足够上下文，绝不能只写 "14..."、"15 ......"。
            - 只返回 JSON 对象，不要输出 markdown 代码块或额外文字。

            输出格式：
            {
              "passages": ["文章正文..."],
              "questionGroups": [
                {"range":"1-8", "type":"tfng", "instruction":"Do the following...", "questions":["题目1原文","题目2原文",...]}，
                {"range":"9-13", "type":"mcq", "instruction":"Choose the correct responses A-P", "options":{"A":"...","B":"...",...}, "questions":["题目9原文","题目10原文",...]}，
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
            - text 必须原样保留 Step1 中的题干，不要压缩、改写或只保留题号。
            - 对于 summary/notes/flow-chart 连续填空题，如果 Step1 提供了完整上下文句子，必须完整返回该句子。
            """;

    /**
     * Workflow Step 1 (legacy combined): Extract passages and question group skeleton (no answers).
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
     * Workflow Step 1A: Extract passages only (focused, short prompt).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> workflowStep1A(String rawText) throws Exception {
        if (!isConfigured()) throw new IllegalStateException("DeepSeek API key未配置");
        String testText = findTestContent(rawText);
        String input = testText.length() > textMaxChars
                ? testText.substring(0, textMaxChars) + "\n...[截断]"
                : testText;
        log.info("Workflow Step1A (passages): sending {} chars to DeepSeek", input.length());
        String content = callDeepSeek(WORKFLOW_STEP1A_PROMPT,
                "请从以下IELTS试题文本中提取阅读文章正文：\n\n" + input,
                4096, 90);
        log.info("Workflow Step1A: response length={}", content.length());
        return objectMapper.readValue(content, Map.class);
    }

    /**
     * Workflow Step 1B: Identify question groups structure only (focused, short prompt).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> workflowStep1B(String rawText) throws Exception {
        if (!isConfigured()) throw new IllegalStateException("DeepSeek API key未配置");
        String testText = findTestContent(rawText);
        String input = testText.length() > textMaxChars
                ? testText.substring(0, textMaxChars) + "\n...[截断]"
                : testText;
        log.info("Workflow Step1B (questions): sending {} chars to DeepSeek", input.length());
        String content = callDeepSeek(WORKFLOW_STEP1B_PROMPT,
                "请从以下IELTS试题文本中识别所有题组结构：\n\n" + input,
                4096, 90);
        log.info("Workflow Step1B: response length={}", content.length());
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
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = SHARED_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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

    // ── AI Chat Assistant ────────────────────────────────────────────────────

    private static final String CHAT_ASSISTANT_PROMPT = """
            你是一个 IELTS 考试 AI 助手。用户正在做一份 IELTS 试卷，会向你提问关于试卷内容、题目解法、语法、词汇等问题。

            规则：
            - 根据提供的试卷上下文（文章、题目）回答用户的问题。
            - 用中文回答，如涉及英文原文则保留英文并加中文解释。
            - 回答简洁有条理，必要时分点列出。
            - 如果用户问的是某道题的答案或解题思路，给出分析过程，不要只给答案。
            - 不要编造试卷中没有的内容。
            """;

    /**
     * AI chat: answer user's question based on exam context.
     * Returns plain text answer (not JSON).
     */
    public String chatWithContext(String examContext, String userQuestion) throws Exception {
        if (!isConfigured()) throw new IllegalStateException("DeepSeek API key未配置");
        if (userQuestion == null || userQuestion.trim().isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        String ctx = examContext == null ? "" : examContext;
        if (ctx.length() > 8000) ctx = ctx.substring(0, 8000) + "\n...[截断]";

        String userMsg = "【试卷上下文】\n" + ctx + "\n\n【用户问题】\n" + userQuestion.trim();
        return callDeepSeek(CHAT_ASSISTANT_PROMPT, userMsg, 1024, 60, false);
    }

    /**
     * Post-processing: detect inline string options like
     * "A cost B falling C technology ..." and convert them into JSON objects.
     */
    @SuppressWarnings("unchecked")
    private void fixInlineStringOptions(Map<String, Object> parsed, String rawText) {
        Object questionsObj = parsed.get("questions");
        if (!(questionsObj instanceof List)) return;
        List<Map<String, Object>> questions = (List<Map<String, Object>>) questionsObj;

        Map<String, String> optionMapFromRaw = null;
        int fixed = 0;
        for (Map<String, Object> q : questions) {
            Object opts = q.get("options");
            if (!(opts instanceof String)) continue;

            Map<String, String> optionMap = parseInlineOptionString((String) opts);
            if (optionMap.size() < 3 && String.valueOf(opts).trim().matches("^[A-Q]$")) {
                if (optionMapFromRaw == null) {
                    optionMapFromRaw = extractOptionDescriptions(rawText);
                }
                optionMap = optionMapFromRaw;
            }
            if (optionMap.size() >= 3) {
                q.put("options", optionMap);
                fixed++;
            }
        }
        if (fixed > 0) {
            log.info("fixInlineStringOptions: converted {} inline option strings", fixed);
        }
    }

    private Map<String, String> parseInlineOptionString(String optionsText) {
        Map<String, String> options = new LinkedHashMap<>();
        if (optionsText == null || optionsText.isBlank()) return options;

        java.util.regex.Matcher inline = INLINE_OPTION_PATTERN.matcher(optionsText.trim());
        while (inline.find()) {
            options.put(inline.group(1), inline.group(2).trim());
        }
        return options;
    }

    /**
     * Post-processing: detect questions with letter-only options (e.g. ["A","B","C",...])
     * and attempt to extract full option descriptions from the raw input text.
     */
    @SuppressWarnings("unchecked")
    private void fixLetterOnlyMcqOptions(Map<String, Object> parsed, String rawText) {
        Object questionsObj = parsed.get("questions");
        if (!(questionsObj instanceof List)) return;
        List<Map<String, Object>> questions = (List<Map<String, Object>>) questionsObj;

        // Find questions with letter-only options (MCQ and word-bank fill questions)
        List<Map<String, Object>> broken = new ArrayList<>();
        for (Map<String, Object> q : questions) {
            Object type = q.get("type");
            if (!"mcq".equals(type) && !"fill".equals(type)) continue;
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
        log.info("fixLetterOnlyMcqOptions: enriching {} questions with {} extracted options", broken.size(), optionMap.size());

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
        if (options.size() < 3) {
            options.clear();
            for (String line : lines) {
                String trimmed = line.trim();
                java.util.regex.Matcher markerCounter = java.util.regex.Pattern
                        .compile("(?<![A-Za-z])([A-Q])\\s+[A-Za-z]")
                        .matcher(trimmed);
                int markerCount = 0;
                while (markerCounter.find()) markerCount++;
                if (markerCount < 2) continue;

                java.util.regex.Matcher inline = INLINE_OPTION_PATTERN.matcher(trimmed);
                while (inline.find()) {
                    options.put(inline.group(1), inline.group(2).trim());
                }
            }
        }
        return options;
    }

    // ─── Range options fix (e.g. "i-vii" → proper heading objects) ────────────

    /**
     * Detect MCQ questions whose options is a range string like "i-vii" or "A-F"
     * and replace with properly extracted heading/option objects from raw text.
     */
    @SuppressWarnings("unchecked")
    private void fixRangeOptions(Map<String, Object> parsed, String rawText) {
        Object questionsObj = parsed.get("questions");
        if (!(questionsObj instanceof List)) return;
        List<Map<String, Object>> questions = (List<Map<String, Object>>) questionsObj;

        Map<String, String> headingMap = null; // lazy-init: regex first, then AI fallback

        for (Map<String, Object> q : questions) {
            if (!"mcq".equals(q.get("type"))) continue;
            Object opts = q.get("options");
            if (opts == null) continue;

            // Detect range string like "i-vii", "i-viii", "A-F", etc.
            if (opts instanceof String) {
                String s = ((String) opts).trim();
                if (s.matches("(?i)[ivx]+-[ivx]+") || s.matches("[A-Z]-[A-Z]")) {
                    if (headingMap == null) headingMap = getHeadingMap(rawText);
                    if (!headingMap.isEmpty()) {
                        q.put("options", new LinkedHashMap<>(headingMap));
                        log.info("fixRangeOptions: replaced range '{}' with {} extracted headings for Q{}",
                                s, headingMap.size(), q.get("questionNumber"));
                    }
                }
            }
            // Detect array of roman numerals ["i","ii","iii",...] without descriptions
            if (opts instanceof List) {
                List<?> list = (List<?>) opts;
                boolean allRomanNumerals = list.size() >= 3 && list.stream().allMatch(o -> {
                    String v = String.valueOf(o).trim().toLowerCase();
                    return v.matches("^(i{1,3}|iv|vi{0,3}|ix|x{1,3})$");
                });
                if (allRomanNumerals) {
                    if (headingMap == null) headingMap = getHeadingMap(rawText);
                    if (!headingMap.isEmpty()) {
                        Map<String, String> enriched = new LinkedHashMap<>();
                        for (Object o : list) {
                            String key = String.valueOf(o).trim().toLowerCase();
                            enriched.put(key, headingMap.getOrDefault(key, key));
                        }
                        q.put("options", enriched);
                        log.info("fixRangeOptions: enriched roman numeral array with {} headings for Q{}",
                                headingMap.size(), q.get("questionNumber"));
                    }
                    continue;
                }
                // Single uppercase letters already handled by fixLetterOnlyMcqOptions
                boolean allSingleChar = list.size() >= 3 && list.stream().allMatch(o -> {
                    String v = String.valueOf(o).trim();
                    return v.length() == 1 && Character.isLetter(v.charAt(0));
                });
                if (allSingleChar) {
                    continue;
                }
            }
            // Detect map where key == value for roman numerals {"i":"i","ii":"ii",...}
            if (opts instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) opts;
                boolean allRomanKV = map.size() >= 3 && map.entrySet().stream().allMatch(e -> {
                    String k = String.valueOf(e.getKey()).trim().toLowerCase();
                    String v = String.valueOf(e.getValue()).trim().toLowerCase();
                    return k.equals(v) && k.matches("^(i{1,3}|iv|vi{0,3}|ix|x{1,3})$");
                });
                if (allRomanKV) {
                    if (headingMap == null) headingMap = getHeadingMap(rawText);
                    if (!headingMap.isEmpty()) {
                        Map<String, String> enriched = new LinkedHashMap<>();
                        for (Object key : map.keySet()) {
                            String k = String.valueOf(key).trim().toLowerCase();
                            enriched.put(k, headingMap.getOrDefault(k, k));
                        }
                        q.put("options", enriched);
                        log.info("fixRangeOptions: enriched roman numeral map with {} headings for Q{}",
                                headingMap.size(), q.get("questionNumber"));
                    }
                }
            }
        }
    }

    /**
     * Get heading descriptions: try regex extraction first, fall back to AI extraction.
     */
    private Map<String, String> getHeadingMap(String rawText) {
        Map<String, String> headings = extractHeadingDescriptions(rawText);
        if (headings.size() >= 3) return headings;
        log.info("getHeadingMap: regex extraction found only {} headings, trying AI fallback", headings.size());
        Map<String, String> aiHeadings = extractHeadingsWithAi(rawText);
        return aiHeadings.size() >= 3 ? aiHeadings : headings;
    }

    /**
     * Extract heading list from raw text, e.g.:
     *   i    Avoiding an overcrowded centre
     *   ii   A successful exercise in people power
     * Returns map like {"i": "Avoiding an overcrowded centre", "ii": "A successful exercise..."}
     */
    private Map<String, String> extractHeadingDescriptions(String rawText) {
        Map<String, String> headings = new LinkedHashMap<>();
        if (rawText == null) return headings;

        String[] lines = rawText.split("\\n");
        String currentKey = null;
        StringBuilder currentText = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            java.util.regex.Matcher m = HEADING_LINE_PATTERN.matcher(trimmed);
            if (m.matches()) {
                if (currentKey != null) {
                    headings.put(currentKey, currentText.toString().trim());
                }
                currentKey = m.group(1).toLowerCase();
                currentText = new StringBuilder(m.group(2));
            } else if (currentKey != null && !trimmed.isEmpty()
                    && !trimmed.matches("^\\d+\\s+.*")
                    && !trimmed.matches("(?i)^questions?\\s+.*")
                    && trimmed.length() < 100) {
                // Continuation line
                currentText.append(" ").append(trimmed);
            } else if (currentKey != null) {
                headings.put(currentKey, currentText.toString().trim());
                currentKey = null;
            }
        }
        if (currentKey != null) {
            headings.put(currentKey, currentText.toString().trim());
        }

        // Also try matching with dots/colons: "i. Heading text" or "i: Heading text"
        if (headings.size() < 3) {
            headings.clear();
            java.util.regex.Pattern dotPattern = java.util.regex.Pattern.compile(
                    "^(i{1,3}|iv|vi{0,3}|ix|x)[.):：]\\s*(.+)$", java.util.regex.Pattern.CASE_INSENSITIVE);
            for (String line : lines) {
                String trimmed = line.trim();
                java.util.regex.Matcher m = dotPattern.matcher(trimmed);
                if (m.matches()) {
                    headings.put(m.group(1).toLowerCase(), m.group(2).trim());
                }
            }
        }
        return headings;
    }

    // ─── Missing heading matching questions fix ──────────────────────────────

    /**
     * Detect heading matching questions (MCQ with "Paragraph X" text and roman numeral options)
     * and auto-generate missing paragraph questions when the AI only produced some of them.
     *
     * For example, if passages contain paragraphs A-E but only Q1 "Paragraph A" exists,
     * this method creates Q2 "Paragraph B", Q3 "Paragraph C", etc. with the same options.
     */
    @SuppressWarnings("unchecked")
    private void fixMissingHeadingQuestions(Map<String, Object> parsed, String rawText) {
        Object questionsObj = parsed.get("questions");
        if (!(questionsObj instanceof List)) return;
        List<Map<String, Object>> questions = (List<Map<String, Object>>) questionsObj;

        // Find existing heading matching questions (text like "Paragraph A", "Paragraph B", etc.)
        Map<String, Map<String, Object>> existingParaQuestions = new LinkedHashMap<>();
        Map<String, Object> templateQuestion = null;
        int templateQuestionNumber = -1;

        for (Map<String, Object> q : questions) {
            String text = String.valueOf(q.getOrDefault("text", "")).trim();
            if ("mcq".equals(q.get("type")) && text.matches("(?i)Paragraph\\s+[A-Z]")) {
                String paraLetter = text.replaceAll("(?i)Paragraph\\s+", "").toUpperCase();
                existingParaQuestions.put(paraLetter, q);
                if (templateQuestion == null) {
                    templateQuestion = q;
                    Object num = q.get("questionNumber");
                    templateQuestionNumber = num instanceof Number ? ((Number) num).intValue() : 1;
                }
            }
        }

        // No heading matching questions found at all — nothing to fix
        if (templateQuestion == null) return;

        // Detect lettered paragraphs in passages and raw text
        List<String> detectedParagraphs = detectParagraphLetters(parsed, rawText);
        if (detectedParagraphs.size() <= 1) return;

        // Check if there are missing paragraph questions
        List<String> missingParas = new ArrayList<>();
        for (String para : detectedParagraphs) {
            if (!existingParaQuestions.containsKey(para)) {
                missingParas.add(para);
            }
        }

        if (missingParas.isEmpty()) return;

        log.info("fixMissingHeadingQuestions: found {} existing paragraph questions, {} paragraphs detected, {} missing: {}",
                existingParaQuestions.size(), detectedParagraphs.size(), missingParas.size(), missingParas);

        // Generate missing questions using the template
        Object templateOptions = templateQuestion.get("options");
        int nextNum = templateQuestionNumber + 1;

        // Determine insertion index: right after the template question
        int insertIdx = questions.indexOf(templateQuestion) + 1;

        for (String para : detectedParagraphs) {
            if (existingParaQuestions.containsKey(para)) {
                // Update question number to maintain order
                Map<String, Object> existing = existingParaQuestions.get(para);
                int existingIdx = questions.indexOf(existing);
                if (existingIdx >= 0) {
                    nextNum = ((Number) existing.getOrDefault("questionNumber", nextNum)).intValue() + 1;
                    insertIdx = existingIdx + 1;
                }
                continue;
            }

            Map<String, Object> newQ = new LinkedHashMap<>();
            newQ.put("questionNumber", nextNum);
            newQ.put("type", "mcq");
            newQ.put("text", "Paragraph " + para);
            if (templateOptions != null) newQ.put("options", templateOptions);
            newQ.put("answer", "");
            newQ.put("explanation", "此题由系统自动补全，请手动作答");
            newQ.put("locatorText", "");

            questions.add(insertIdx, newQ);
            insertIdx++;
            nextNum++;
            log.info("fixMissingHeadingQuestions: generated Q{} 'Paragraph {}'", newQ.get("questionNumber"), para);
        }

        // Re-number sequential heading questions starting from the template
        renumberHeadingQuestions(questions, templateQuestionNumber, detectedParagraphs);
    }

    /**
     * Detect lettered paragraphs (A, B, C, ...) from passages and raw text.
     */
    @SuppressWarnings("unchecked")
    private List<String> detectParagraphLetters(Map<String, Object> parsed, String rawText) {
        Set<String> found = new LinkedHashSet<>();
        String combined = "";

        // Check passages
        Object passagesObj = parsed.get("passages");
        if (passagesObj instanceof List) {
            combined = String.join("\n", (List<String>) passagesObj);
        }
        if (rawText != null) combined += "\n" + rawText;

        // Look for paragraph markers like "A In fact,", "B In the UK,", etc.
        // These are typically a single letter at the start of a line followed by a space and text
        java.util.regex.Pattern paraPattern = java.util.regex.Pattern.compile(
                "(?m)^\\s*([A-Z])\\s+[A-Z][a-z]");
        java.util.regex.Matcher m = paraPattern.matcher(combined);
        while (m.find()) {
            found.add(m.group(1));
        }

        // Sort alphabetically
        List<String> sorted = new ArrayList<>(found);
        sorted.sort(String::compareTo);

        // Only return if we have at least 2 consecutive letters (to avoid false positives)
        if (sorted.size() >= 2) {
            return sorted;
        }
        return List.of();
    }

    /**
     * Re-number heading matching questions to be sequential.
     */
    private void renumberHeadingQuestions(List<Map<String, Object>> questions, int startNum, List<String> paragraphs) {
        int num = startNum;
        for (String para : paragraphs) {
            for (Map<String, Object> q : questions) {
                String text = String.valueOf(q.getOrDefault("text", "")).trim();
                if (text.equalsIgnoreCase("Paragraph " + para)) {
                    q.put("questionNumber", num);
                    break;
                }
            }
            num++;
        }
    }

    // ─── AI-based heading extraction fallback ─────────────────────────────────

    private static final String HEADING_EXTRACTION_PROMPT = """
            你是一个雅思（IELTS）标题列表提取器。给定的文本中包含一个"List of Headings"，
            其中每个选项都是罗马数字编号（i, ii, iii, iv, v, vi, vii, viii, ix, x 等）加上对应的标题描述。

            请提取所有标题选项，返回 JSON 对象。

            规则：
            - key 为小写罗马数字（i, ii, iii, ...）
            - value 为该标题的完整英文描述文字
            - 只返回 JSON 对象，不要 markdown 代码块或额外文字
            - 如果找不到标题列表，返回空对象 {}

            输出格式：
            {"i":"Avoiding an overcrowded centre","ii":"A successful exercise in people power",...}
            """;

    /**
     * Fallback: use AI to extract heading descriptions when regex extraction fails.
     * Returns a map of roman numeral -> heading description, or empty map if failed.
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> extractHeadingsWithAi(String rawText) {
        if (!isConfigured() || rawText == null) return Map.of();
        try {
            String input = rawText.length() > textMaxChars
                    ? rawText.substring(0, textMaxChars) : rawText;
            log.info("extractHeadingsWithAi: calling AI to extract heading list...");
            String content = callDeepSeek(HEADING_EXTRACTION_PROMPT,
                    "请从以下IELTS试题文本中提取 List of Headings 中的所有标题选项：\n\n" + input,
                    1024, 60);
            Map<String, String> result = objectMapper.readValue(content,
                    objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, String.class));
            log.info("extractHeadingsWithAi: extracted {} headings", result.size());
            return result;
        } catch (Exception e) {
            log.warn("extractHeadingsWithAi failed: {}", e.getMessage());
            return Map.of();
        }
    }

    // ─── locatorText validation & correction ─────────────────────────────────

    /**
     * Validate each question's locatorText against passages + raw input text.
     * If locatorText is not found verbatim, attempt fuzzy correction by searching
     * for the best overlapping substring in the combined text.
     */
    @SuppressWarnings("unchecked")
    private void fixLocatorText(Map<String, Object> parsed, String rawText) {
        Object questionsObj = parsed.get("questions");
        if (!(questionsObj instanceof List)) return;
        List<Map<String, Object>> questions = (List<Map<String, Object>>) questionsObj;

        // Build combined search text from passages + raw input
        StringBuilder sb = new StringBuilder();
        Object passagesObj = parsed.get("passages");
        if (passagesObj instanceof List) {
            for (Object p : (List<?>) passagesObj) sb.append(p).append("\n");
        }
        sb.append("\n").append(rawText != null ? rawText : "");
        String combined = sb.toString().toLowerCase();

        int fixed = 0;
        for (Map<String, Object> q : questions) {
            String locator = q.get("locatorText") != null ? q.get("locatorText").toString().trim() : "";
            if (locator.isEmpty()) continue;

            // Check if locatorText exists verbatim (case-insensitive)
            if (combined.contains(locator.toLowerCase())) continue;

            // Not found — try to find the best matching window in combined text
            String corrected = findBestMatch(combined, locator.toLowerCase());
            if (corrected != null && !corrected.isEmpty()) {
                q.put("locatorText", corrected);
                fixed++;
            }
        }
        if (fixed > 0) {
            log.info("fixLocatorText: corrected {} locatorText entries", fixed);
        }
    }

    /**
     * Find the substring in `text` that best matches `target` using word-overlap scoring.
     * Returns the best matching window of similar word count, or null if no reasonable match.
     */
    private String findBestMatch(String textLower, String targetLower) {
        String[] targetWords = targetLower.split("\\s+");
        if (targetWords.length < 2) return null;

        // Build a set of meaningful target words (length >= 3)
        Set<String> targetSet = new HashSet<>();
        for (String w : targetWords) {
            if (w.length() >= 3) targetSet.add(w);
        }
        if (targetSet.size() < 2) return null;

        // Split combined text into sentences by newline / period
        String[] sentences = textLower.split("[\\n.]+");
        String bestSentence = null;
        double bestScore = 0;

        for (String sent : sentences) {
            String trimmed = sent.trim();
            if (trimmed.length() < 10) continue;
            String[] words = trimmed.split("\\s+");
            int hits = 0;
            for (String w : words) {
                if (targetSet.contains(w)) hits++;
            }
            double score = (double) hits / targetSet.size();
            if (score > bestScore) {
                bestScore = score;
                bestSentence = trimmed;
            }
        }

        // Require at least 40% word overlap to consider it a match
        if (bestScore < 0.4 || bestSentence == null) return null;

        // Extract a window of ~targetWords.length words from the best sentence
        String[] sentWords = bestSentence.split("\\s+");
        int windowSize = Math.min(targetWords.length + 2, sentWords.length);
        int bestStart = 0;
        int bestHits = 0;
        for (int i = 0; i <= sentWords.length - windowSize; i++) {
            int hits = 0;
            for (int j = i; j < i + windowSize; j++) {
                if (targetSet.contains(sentWords[j])) hits++;
            }
            if (hits > bestHits) {
                bestHits = hits;
                bestStart = i;
            }
        }
        StringBuilder result = new StringBuilder();
        for (int i = bestStart; i < bestStart + windowSize && i < sentWords.length; i++) {
            if (result.length() > 0) result.append(" ");
            result.append(sentWords[i]);
        }
        return result.toString();
    }
}
