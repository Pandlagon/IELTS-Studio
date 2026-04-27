package com.ieltsstudio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Qwen 视觉模型精准解析服务。
 * 将 PDF 页面 / 图片直接发送给 Qwen-VL，由视觉模型一步到位输出结构化 JSON，
 * 无需先转文本再交给 DeepSeek。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QwenAiParseService {

    @Value("${qwen.api-key:}")
    private String apiKey;

    @Value("${qwen.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Value("${qwen.model:qwen-vl-plus}")
    private String model;

    @Value("${qwen.max-pages:20}")
    private int maxPages;

    @Value("${qwen.render-dpi:160}")
    private int renderDpi;

    @Value("${qwen.image-quality:0.82}")
    private float imageQuality;

    @Value("${qwen.ai-max-tokens:8192}")
    private int aiMaxTokens;

    private final ObjectMapper objectMapper;

    // ── Base prompts ───────────────────────────────────────────────────────
    
    private static final String BASE_SYSTEM_PROMPT = """
        你是一个具备视觉能力的雅思考试内容解析器。给定考试试卷页面的图片，读取所有可见文本、表格、图表、图示和图片，然后解析并返回一个结构化的 JSON 对象。
        """;
    
    private static final String READING_PROMPT = """
        你是一个具备视觉能力的雅思阅读试卷解析器。

        “passages”：字符串数组
          - READING：阅读文本，每篇文章包含标题+正文
          - 【极其重要】passages 数组【绝对不能包含任何题目、问题编号、选项或指令文字】
          - 【极其重要】正文只包含文章/阅读段落内容，题目必须全部放入 questions 数组
          - 阅读部分的【关键分离规则】：
            * passages 数组必须【只包含文章/阅读正文】
            * 【不要】包含任何问题、问题编号、选项或指令文字
            * 遇到第一个问题（例如“Question 1”、“1.”、“Q1”等）时，【立即停止】提取 passage 内容
            * 应停止的问题标记包括：“Question”、“Q”、编号列表如“1.”、“2.”、“3.”，选项标记如“A)”、“B)”、“C)”、“D)”、“True/False/Not Given”
            * 文章标记如“P11”、“P12”、“Passage 1”、“Reading Passage”应保留在 passages 中
            * 如果页面中 passage 文本与问题混合，只提取 passage 部分放入 passages 数组

        “questions”：问题对象数组，根据类型包含不同字段：

          ── 阅读问题类型 ──────────────────────────────────────────────
          类型 "tfng"  → 判断题（True/False/Not Given 或 Yes/No/Not Given）
            字段：questionNumber, type, text, answer（“TRUE”/“FALSE”/“NOT GIVEN”
                  或“YES”/“NO”/“NOT GIVEN”）, explanation, locatorText
          类型 "mcq"   → 单选题（带字母选项）
            字段：questionNumber, type, text, options（JSON对象 {"A":"完整选项文字","B":"完整选项文字",…}）, answer（"A"/"B"/…）,
                  explanation, locatorText
            - 【重要】options 必须是 JSON 对象（不是数组），每个值必须是选项的完整描述文字，不能只写字母标识。
            - 【重要】如果一组题目共享同一个选项列表（如 Questions 9-13 从 A-P 选项中选择），每道题的 options 都必须包含完整选项列表。
          类型 "fill"  → 填空/简答/句子完成/标题题
            字段：questionNumber, type, text, answer（单词或短语）, explanation, locatorText

        【重要】题型判定规则：
        - 如果题目要求从一个选项列表（如 A-P、A-H 等）中选择答案，必须标记为 "mcq"，并在 options 中列出所有可选项。
        - options 必须是 JSON 对象格式，每个选项值必须是完整的选项描述文字（不能只写字母标识）。
          例如：{"A":"Rainforests are in danger.","B":"Climate change is the main threat.","C":"..."}
        - 如果一组题目共享同一个选项列表（如 Questions 9-13 从 A-P 中选），每道题的 options 都必须包含完整选项列表。
        - 只有答案是从文章中提取的单词或短语时才使用 "fill"。
        - 选项列表可能出现在一组题目的前面或后面，需要关联到对应的题目。
        - 匹配题（matching）也属于 "mcq"，需要将匹配列表作为 options 提供。

        规则：
        - 【极其重要】对于阅读部分：所有问题都放入 questions 数组，passages 数组中【绝对不能包含任何题目】。
        - 【极其重要】正文（passages）只包含文章/阅读段落内容，题目必须全部放入 questions 数组。
        - 保留原始问题编号（1, 2, 3...）。
        - answer：如果答案区域有答案则复制；否则从 passage 中推导。
        - locatorText：来自 passage 的【短小原词短语（3-8个词）】，不可为空。
        - 只返回 JSON 对象，不要用 markdown 代码块包裹，不要输出任何额外文字。
        """;
    
    private static final String WRITING_PROMPT = """
        你是一个具备视觉能力的雅思写作试卷解析器。返回一个 JSON 对象。

        ── PASSAGES ────────────────────────────────────────────────────────────
        “passages”：字符串数组，每个写作任务（Task 1、Task 2）一个条目。

        对于 TASK 2（议论文）：必须逐字包含所有考试指令文字，例如 "WRITING TASK 2"、"You should spend about 40 minutes on this task."、"Write about the following topic:"、"Write at least 250 words." 等。不要省略任何原文中的文字。不需要特殊区块。

        对于 TASK 1（描述图表）：passage 字符串必须【按顺序】包含以下区块，区块外不得有任何额外文字：

          [Task Prompt]
          <逐字复制完整题目指令，例如“The chart below shows...
           Summarise the information by selecting and reporting the main features...”>

          [Visual Data Summary]
          chartTitle: <图表的主标题，如果有的话>
          chartType: <以下之一：pie chart | bar chart | line graph | table | map | process diagram>
          <分类标签 1>: <数值><单位>
          <分类标签 2>: <数值><单位>
          <分类标签 3>: <数值><单位>
          ... （每行一个数据点，每行必须严格遵循“标签: 数值 单位”格式）

          [Table Data]
          tableTitle: <表格标题，如果有的话>
          | 列名1 | 列名2 | 列名3 |
          |---------|---------|---------|
          | 值1   | 值2   | 值3   |
          （仅当原图中存在表格时才包含此区块；无表格时完全省略）

        视觉数据摘要规则：
        - chartTitle 行必须放在第一行（如果有标题），单独一行，格式：“chartTitle: 主标题”
        - chartType 行必须放在第二行，单独一行，格式：“chartType: bar chart”
        - 后续每一行是一个数据点：“标签: 数值 单位”
          正确示例：  “Over-grazing: 35%”
          正确示例：  “North America: 120 million”
          错误示例：  “keyValues: Over-grazing (35%), Deforestation (30%)”  ← 绝对禁止
          错误示例：  “Over-grazing (35%)”  ← 没有冒号分隔符
        - 不要使用 keyCategories 或 keyValues 等字段名 —— 直接逐行列出数据点
        - 包含图表中所有可见的数据点（最多 15 个）
        - 保留原始标签的准确表述（地区名、年份标签、类别名）
        - 原样包含图表标题、坐标轴标签、图例文字以及任何副标题

        表格格式规则（仅当存在表格时）：
        - 如果表格有标题，在表格上方第一行添加 tableTitle: <表格标题>
        - 每行必须具有相同数量的列
        - 分隔行使用短横线：|---|---|
        - 关键：第一行必须是表头行，包含所有列名
        - 第一列不能留空，必须包含行标签（例如“Region”）
        - 对于多级表头：每列重复父级值
          例如，如果“Region”跨越 3 个子列 → | Region | Region | Region |
        - 不要编造数据；逐单元格原样转录
        - 在第一列中包含所有行标签（例如“North America”、“Europe”、“Oceania”）

        ── QUESTIONS ───────────────────────────────────────────────────────────
        “questions”：数组，每个写作任务对应一个对象。

          类型 "write"
            questionNumber  : 整数（Task 1 为 1，Task 2 为 2）
            type            : "write"
            taskType        : "Task1" 或 "Task2"
            wordLimit       : 150（Task 1）或 250（Task 2）
            text            : 完整的题目提示文字（与上方 [Task Prompt] 区块相同）
            answer          : 建议的写作思路/关键点，≤60 字
            explanation     : 一句话说明高分作文应覆盖哪些要点
            locatorText     : 来自题目提示文字的【原词短语（4–8 个词）】

        ── 规则 ───────────────────────────────────────────────────────────────
        - 如果 Task 1 和 Task 2 同时出现，则 passages[] 中包含两个条目，questions[] 中也包含两个对象。
        - locatorText 必须来自题目提示文字，不得自行编造。
        - 关键：不要修改、改动或重写原图中的任何文字。逐字复制所有文本，包括题目指令、图表标题、标签和表格内容。不允许总结或转述。
        - 只返回 JSON 对象 —— 不要用 markdown 代码块包裹，不要输出任何额外文字。

        ── 示例 ─────────────────────────────────────────────────────────────
        {
          "passages": [
            "[Task Prompt]\\nThe pie chart below shows the causes of land degradation worldwide. Summarise the information by selecting and reporting the main features.\\n\\n[Visual Data Summary]\\nchartTitle: Causes of land degradation worldwide\\nchartType: pie chart\\nOver-grazing: 35%\\nDeforestation: 30%\\nOver-cultivation: 28%\\nOther: 7%\\n\\n[Table Data]\\n| Cause | Percentage |\\n|---|---|\\n| Over-grazing | 35% |\\n| Deforestation | 30% |",
            "Some people believe that unpaid community service should be a compulsory part of high school programmes. To what extent do you agree or disagree?"
          ],
          "questions": [
            {
              "questionNumber": 1,
              "type": "write",
              "taskType": "Task1",
              "wordLimit": 150,
              "text": "The pie chart below shows the causes of land degradation worldwide. Summarise the information by selecting and reporting the main features.",
              "answer": "Introduce the chart, highlight over-grazing as the largest cause at 35%, group the top three causes accounting for 93%, and note the minor contribution of other factors.",
              "explanation": "A band-7+ response overview-first, compares proportions using precise percentages, and avoids listing every figure without analysis.",
              "locatorText": "causes of land degradation worldwide"
            },
            {
              "questionNumber": 2,
              "type": "write",
              "taskType": "Task2",
              "wordLimit": 250,
              "text": "Some people believe that unpaid community service should be a compulsory part of high school programmes. To what extent do you agree or disagree?",
              "answer": "Partially agree: compulsory service builds civic responsibility and empathy, but forced participation may reduce genuine engagement; a better approach is incentivised voluntary schemes.",
              "explanation": "High-band essays take a clear position, support it with developed arguments, and acknowledge the counter-view.",
              "locatorText": "unpaid community service compulsory part"
            }
          ]
        }
        """;
    
    private static final String LISTENING_PROMPT = """
        你是一个具备视觉能力的雅思听力试卷解析器。

        “passages”：字符串数组
          - 录音原文文本，如果没有提供脚本则为空数组
          - 如果可见录音原文，则准确转录所有文本

        “questions”：问题对象数组：
          类型 "tfng"  → {questionNumber, type, text, answer, explanation, locatorText}
          类型 "mcq"   → {questionNumber, type, text, options{"A":...}, answer, explanation, locatorText}
          类型 "fill"  → {questionNumber, type, text, answer, explanation, locatorText}

        规则：
        - 保留原始问题编号。
        - answer：如果有答案区域则复制答案；否则从录音原文中推导。
        - locatorText：来自录音原文或问题关键词的短小原词短语（3-8个词）。
        - 只返回 JSON 对象，不要用 markdown 代码块包裹，不要输出任何额外文字。
        """;
    
    // ── 多页试卷提示词 ──────────────────────────────────────────────────
    
    private static final String READING_MULTI_PAGE_PROMPT = """
            你是一个具备视觉能力的雅思阅读试卷解析器，支持多页试卷。

            对于每个部分，完整解析如下：
            “passages”：字符串数组
              - READING：阅读文本，每篇文章包含标题+正文
              - 阅读部分的【关键分离规则】：
                * passages 数组必须【只包含文章/阅读正文】
                * 【不要】包含任何问题、问题编号、选项或指令文字
                * 遇到第一个问题（例如“Question 1”、“1.”、“Q1”等）时，【立即停止】提取 passage 内容
                * 应停止的问题标记包括：“Question”、“Q”、编号列表如“1.”、“2.”、“3.”，选项标记如“A)”、“B)”、“C)”、“D)”、“True/False/Not Given”
                * 文章标记如“P11”、“P12”、“Passage 1”、“Reading Passage”应保留在 passages 中
                * 如果页面中 passage 文本与问题混合，只提取 passage 部分放入 passages 数组

            “questions”：问题对象数组：
              类型 "tfng"  → {questionNumber, type, text, answer, explanation, locatorText}
              类型 "mcq"   → {questionNumber, type, text, options{"A":...}, answer, explanation, locatorText}
              类型 "fill"  → {questionNumber, type, text, answer, explanation, locatorText}

            【重要】题型判定规则：
            - 如果题目要求从一个选项列表（如 A-P、A-H 等）中选择答案，必须标记为 "mcq"，并在 options 中列出所有可选项。
            - 只有答案是从文章中提取的单词或短语时才使用 "fill"。
            - 匹配题（matching）和列表选择题也属于 "mcq"。

            规则：
            - 对于阅读部分：所有问题都放入 questions 数组，passages 数组中【不包含任何问题】。
            - 在每个部分内保留原始问题编号。
            - 只返回有效的 JSON，不要用 markdown 代码块包裹。

            返回格式：
            {"sections":[{"name":"阅读题1","type":"reading","passages":[...],"questions":[...]},...]}
            """;
    
    private static final String WRITING_MULTI_PAGE_PROMPT = """
            你是一个具备视觉能力的雅思写作试卷解析器，支持多页试卷。

            对于每个部分，完整解析如下：
            "passages"：字符串数组
              - 写作任务的【完整原文】（每个任务一个条目），必须逐字包含所有考试指令，例如 "WRITING TASK 2"、"You should spend about 40 minutes on this task."、"Write about the following topic:"、"Write at least 250 words." 等。不要省略任何原文中的文字。如果存在图表/图形/表格，你必须包含 chartType 和所有数据。

            写作任务 1（带图表）的重要规则：
            - 始终在 passage 开头包含 chartType。
            - 格式示例："chartType: pie chart\\n\\n[Task Prompt]\\n...\\n\\n[Table Data]\\n|...|...|"

            “questions”：问题对象数组：
              类型 "write" → {questionNumber, type, text, taskType, answer（≤60字）, explanation, \
            locatorText, wordLimit}

            规则：
            - 在每个部分内保留原始问题编号。
            - 只返回有效的 JSON，不要用 markdown 代码块包裹。

            返回格式：
            {"sections":[{"name":"写作题1","type":"writing","passages":[...],"questions":[...]},...]}
            """;
    
    private static final String LISTENING_MULTI_PAGE_PROMPT = """
            你是一个具备视觉能力的雅思听力试卷解析器，支持多页试卷。

            对于每个部分，完整解析如下：
            “passages”：字符串数组
              - 录音原文文本，如果没有提供脚本则为空数组

            “questions”：问题对象数组：
              类型 "tfng"  → {questionNumber, type, text, answer, explanation, locatorText}
              类型 "mcq"   → {questionNumber, type, text, options{"A":...}, answer, explanation, locatorText}
              类型 "fill"  → {questionNumber, type, text, answer, explanation, locatorText}

            规则：
            - 在每个部分内保留原始问题编号。
            - 只返回有效的 JSON，不要用 markdown 代码块包裹。

            返回格式：
            {"sections":[{"name":"听力题1","type":"listening","passages":[...],"questions":[...]},...]}
            """;

    // Legacy prompts (kept for backward compatibility)
    private static final String SYSTEM_PROMPT = BASE_SYSTEM_PROMPT + READING_PROMPT;
    private static final String MULTI_PAGE_PROMPT = READING_MULTI_PAGE_PROMPT;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    // ── Public entry point ──────────────────────────────────────────────────

    /**
     * Parse a PDF or image file directly into structured exam JSON using Qwen vision.
     * Returns a Map with either {"passages":...,"questions":...} or {"sections":[...]}.
     * 
     * @param fileBytes The file content as bytes
     * @param filename The original filename
     * @param examType The exam type (reading/writing/listening), defaults to "reading"
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseDocument(byte[] fileBytes, String filename, String examType) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Qwen API key 未配置，请在 application.yml 中设置 qwen.api-key");
        }
        String lower = filename == null ? "" : filename.toLowerCase();
        
        // Default to reading if examType is null or empty
        if (examType == null || examType.isBlank()) {
            examType = "reading";
        }

        List<String> dataUrls;
        if (lower.endsWith(".pdf")) {
            dataUrls = pdfToDataUrls(fileBytes);
        } else if (lower.matches(".*\\.(png|jpg|jpeg|webp|bmp)$")) {
            String mime = detectMimeType(filename);
            dataUrls = List.of("data:" + mime + ";base64," + Base64.getEncoder().encodeToString(fileBytes));
        } else {
            throw new RuntimeException("Qwen 精准解析当前仅支持 PDF 或图片文件");
        }

        if (dataUrls.size() == 1) {
            return parseSingleImage(dataUrls.get(0), examType);
        } else {
            return parseMultiPageImages(dataUrls, examType);
        }
    }

    /**
     * Legacy method for backward compatibility - defaults to reading type
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseDocument(byte[] fileBytes, String filename) throws Exception {
        return parseDocument(fileBytes, filename, "reading");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> parseImages(List<byte[]> imageBytes, List<String> filenames, String examType) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Qwen API key 未配置，请在 application.yml 中设置 qwen.api-key");
        }
        if (imageBytes == null || imageBytes.isEmpty()) {
            throw new RuntimeException("未提供图片数据");
        }
        if (examType == null || examType.isBlank()) {
            examType = "reading";
        }
        List<String> dataUrls = new ArrayList<>();
        for (int i = 0; i < imageBytes.size(); i++) {
            byte[] bytes = imageBytes.get(i);
            if (bytes == null || bytes.length == 0) continue;
            String name = (filenames != null && i < filenames.size()) ? filenames.get(i) : null;
            String mime = detectMimeType(name);
            dataUrls.add("data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes));
        }
        if (dataUrls.isEmpty()) {
            throw new RuntimeException("未提供图片数据");
        }
        if (dataUrls.size() == 1) {
            return parseSingleImage(dataUrls.get(0), examType);
        }
        return parseMultiPageImages(dataUrls, examType);
    }

    // ── Prompt selection ─────────────────────────────────────────────────────

    private String getPromptForExamType(String examType, boolean isMultiPage) {
        String type = examType.toLowerCase();
        if (isMultiPage) {
            return switch (type) {
                case "writing" -> BASE_SYSTEM_PROMPT + WRITING_MULTI_PAGE_PROMPT;
                case "listening" -> BASE_SYSTEM_PROMPT + LISTENING_MULTI_PAGE_PROMPT;
                default -> BASE_SYSTEM_PROMPT + READING_MULTI_PAGE_PROMPT;
            };
        } else {
            return switch (type) {
                case "writing" -> BASE_SYSTEM_PROMPT + WRITING_PROMPT;
                case "listening" -> BASE_SYSTEM_PROMPT + LISTENING_PROMPT;
                default -> BASE_SYSTEM_PROMPT + READING_PROMPT;
            };
        }
    }

    // ── Single image → structured JSON ──────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSingleImage(String dataUrl, String examType) throws Exception {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text",
                "请仔细阅读这张IELTS试卷页面图片中的所有文字、表格、图表，然后返回结构化JSON："));
        content.add(Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)));

        String prompt = getPromptForExamType(examType, false);
        String result = callQwen(prompt, content);
        return normalizeParsedResult(readJsonMap(result));
    }

    // ── Multi-page → structured JSON with sections ──────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMultiPageImages(List<String> dataUrls, String examType) throws Exception {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text",
                "以下是同一份IELTS试卷的多页图片，请整体阅读所有页面中的文字、表格、图表，" +
                "检测试题分区并分别解析，返回结构化JSON："));
        for (String url : dataUrls) {
            content.add(Map.of("type", "image_url", "image_url", Map.of("url", url)));
        }

        String prompt = getPromptForExamType(examType, true);
        String result = callQwen(prompt, content);
        Map<String, Object> parsed = normalizeParsedResult(readJsonMap(result));

        // Normalise: if AI returned flat passages/questions instead of sections wrapper, wrap it
        if (!parsed.containsKey("sections") && parsed.containsKey("questions")) {
            Map<String, Object> section = new LinkedHashMap<>(parsed);
            section.putIfAbsent("name", "");
            section.putIfAbsent("type", "reading");
            parsed = Map.of("sections", List.of(section));
        }
        return parsed;
    }

    // ── Call Qwen API ───────────────────────────────────────────────────────

    private String callQwen(String systemPrompt, List<Map<String, Object>> userContent) throws Exception {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.1);
        requestBody.put("max_tokens", aiMaxTokens);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)
        ));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(180))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Qwen AI parse failed HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("choices").path(0).path("message").path("content").asText();
        log.info("Qwen AI parse response length={}", content.length());

        // Strip markdown code fences if present
        if (content.startsWith("```")) {
            content = content.replaceAll("^```[a-z]*\\n?", "").replaceAll("\\n?```$", "").trim();
        }
        return content;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeParsedResult(Map<String, Object> parsed) {
        if (parsed == null) return Map.of();

        if (parsed.containsKey("sections")) {
            Object secObj = parsed.get("sections");
            if (secObj instanceof List<?> secList) {
                for (Object s : secList) {
                    if (!(s instanceof Map<?, ?>)) continue;
                    Map<String, Object> section = (Map<String, Object>) s;
                    normalizePassages(section);
                    normalizeQuestions(section);
                }
            }
        } else {
            normalizePassages(parsed);
            normalizeQuestions(parsed);
        }
        return parsed;
    }

    @SuppressWarnings("unchecked")
    private void normalizePassages(Map<String, Object> node) {
        Object pObj = node.get("passages");
        if (!(pObj instanceof List<?> pList)) return;

        List<String> normalized = new ArrayList<>();
        for (Object p : pList) {
            String text = coercePassageBlock(p);
            if (text.isBlank()) continue;
            text = normalizePassageText(text);
            // Filter out question-like content from passages for reading sections
            text = filterQuestionContent(text);
            normalized.add(text);
        }
        node.put("passages", normalized);
    }

    /**
     * Filter out question-like content from passage text.
     * This removes lines that look like questions to prevent mixing article and questions.
     */
    private String filterQuestionContent(String text) {
        if (text == null || text.isBlank()) return "";
        String[] lines = text.split("\n");
        List<String> filtered = new ArrayList<>();
        boolean inQuestionBlock = false;
        int consecutiveQuestionLines = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                filtered.add(line);
                continue;
            }

            // Check if this is a passage marker (P11, P12, etc.) - always keep these
            boolean isPassageMarker = Pattern.compile("^(P\\d+|Passage \\d+|Reading Passage \\d+).*", Pattern.CASE_INSENSITIVE).matcher(trimmed).matches();
            if (isPassageMarker) {
                inQuestionBlock = false;
                consecutiveQuestionLines = 0;
                filtered.add(line);
                continue;
            }

            // Detect question patterns - more comprehensive detection
            boolean isQuestionLine = 
                // Question numbers: "1.", "2.", "Question 1", "Q1", etc.
                Pattern.compile("^(\\d+\\.|Question\\s*\\d+|Q\\d+|\\d+\\s*\\.|第\\d+题).*", Pattern.CASE_INSENSITIVE).matcher(trimmed).matches()
                // Option markers: "A)", "B)", "C)", "D)", "a.", "b.", etc.
                || trimmed.matches("^[A-Da-d][\\).\\]]\\s*.*")
                // TFNG answers
                || Pattern.compile("^(TRUE|FALSE|NOT GIVEN|YES|NO)\\b.*", Pattern.CASE_INSENSITIVE).matcher(trimmed).matches()
                // Question starter words (case insensitive)
                || Pattern.compile("^(Choose|Select|Which|What|When|Where|Why|How|Who|Complete|Fill|Write|Match|Label|Circle|Tick|Underline).*", Pattern.CASE_INSENSITIVE).matcher(trimmed).matches()
                // Chinese question patterns
                || trimmed.matches("^第.*题.*")
                || trimmed.matches("^选项.*")
                || Pattern.compile("^A\\..*|B\\..*|C\\..*|D\\..*", Pattern.CASE_INSENSITIVE).matcher(trimmed).matches()
                // Instructions like "Questions 1-5"
                || Pattern.compile("^Questions?\\s*\\d+.*", Pattern.CASE_INSENSITIVE).matcher(trimmed).matches();

            if (isQuestionLine) {
                inQuestionBlock = true;
                consecutiveQuestionLines++;
                // Skip this line as it's question content
                continue;
            }

            // If we see multiple consecutive lines that look like article text (long, no question markers)
            // exit question block
            if (inQuestionBlock && trimmed.length() > 60 && 
                !trimmed.matches("^[A-Da-d][\\).\\]]\\s*") &&
                !Pattern.compile("^(TRUE|FALSE|NOT GIVEN|YES|NO)\\b", Pattern.CASE_INSENSITIVE).matcher(trimmed).matches()) {
                consecutiveQuestionLines++;
                if (consecutiveQuestionLines > 2) {
                    inQuestionBlock = false;
                }
            } else if (inQuestionBlock) {
                consecutiveQuestionLines++;
                // If we've been in question block for too many lines without article text, assume it's still questions
                if (consecutiveQuestionLines > 10) {
                    inQuestionBlock = false;
                }
            } else {
                consecutiveQuestionLines = 0;
            }

            if (!inQuestionBlock) {
                filtered.add(line);
            }
        }

        // Remove trailing empty lines and normalize line breaks
        String result = String.join("\n", filtered).replaceAll("\n{3,}", "\n\n").trim();
        
        // Additional check: if result is too short after filtering, return original with warning log
        if (result.length() < 50 && text.length() > 200) {
            log.warn("filterQuestionContent removed too much content, returning original. Original length: {}, Filtered length: {}", text.length(), result.length());
            return text;
        }
        
        return result;
    }

    @SuppressWarnings("unchecked")
    private String coercePassageBlock(Object block) {
        if (block == null) return "";
        if (block instanceof String s) {
            return s;
        }
        if (block instanceof Map<?, ?> map) {
            Object textObj = map.containsKey("text") ? map.get("text") : map.get("content");
            String text = textObj == null ? "" : String.valueOf(textObj);
            if (text.isBlank()) {
                Object title = map.get("title");
                Object body = map.get("body");
                StringBuilder tmp = new StringBuilder();
                if (title != null) tmp.append(title);
                if (body != null) {
                    if (tmp.length() > 0) tmp.append('\n');
                    tmp.append(body);
                }
                text = tmp.toString();
            }
            String type = map.get("type") == null ? "" : String.valueOf(map.get("type")).toLowerCase(Locale.ROOT);
            StringBuilder sb = new StringBuilder();
            String tagged = appendBlockTag(text, type);
            if (!tagged.isBlank()) sb.append(tagged);
            Object children = map.get("children");
            if (children instanceof List<?> list && !list.isEmpty()) {
                for (Object child : list) {
                    String childText = coercePassageBlock(child);
                    if (childText.isBlank()) continue;
                    if (sb.length() > 0) sb.append("\n\n");
                    sb.append(childText);
                }
            }
            return sb.toString();
        }
        if (block instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                String part = coercePassageBlock(item);
                if (part.isBlank()) continue;
                if (sb.length() > 0) sb.append("\n\n");
                sb.append(part);
            }
            return sb.toString();
        }
        return String.valueOf(block);
    }

    private String appendBlockTag(String text, String type) {
        String content = text == null ? "" : text.trim();
        if (content.isEmpty()) return "";
        String tag = switch (type) {
            case "task_prompt" -> "[Task Prompt]";
            case "visual_data" -> "[Visual Data Summary]";
            case "table_data" -> "[Table Data]";
            default -> "";
        };
        String enriched = content;
        if (!tag.isEmpty()) {
            String lower = content.toLowerCase(Locale.ROOT);
            if (!lower.contains(tag.toLowerCase(Locale.ROOT))) {
                enriched = tag + "\n" + content;
            }
            if ("visual_data".equals(type)) {
                enriched = enriched.replaceAll("(?i)\\s+-\\s+(?=key|chart|category|value)", "\n- ");
            }
        }
        return enriched;
    }

    @SuppressWarnings("unchecked")
    private void normalizeQuestions(Map<String, Object> node) {
        Object qObj = node.get("questions");
        if (!(qObj instanceof List<?> qList)) return;

        for (Object q : qList) {
            if (!(q instanceof Map<?, ?>)) continue;
            Map<String, Object> qm = (Map<String, Object>) q;
            Object text = qm.get("text");
            if (text != null) qm.put("text", normalizePassageText(String.valueOf(text)));
            Object locator = qm.get("locatorText");
            if (locator != null) qm.put("locatorText", String.valueOf(locator).replaceAll("\\s+", " ").trim());
        }
    }

    private String normalizePassageText(String input) {
        if (input == null || input.isBlank()) return "";
        String text = input.replace("\r\n", "\n").replace("\r", "\n");

        String[] lines = text.split("\\n");
        List<String> out = new ArrayList<>(lines.length);
        for (String raw : lines) {
            String line = raw == null ? "" : raw.stripTrailing();
            if (line.contains("|")) {
                String compact = line.trim().replaceAll("\\s*\\|\\s*", " | ");
                if (!compact.startsWith("|")) compact = "| " + compact;
                if (!compact.endsWith("|")) compact = compact + " |";
                line = compact.replaceAll("\\s{2,}", " ");
            }
            out.add(line);
        }

        String normalized = String.join("\n", out)
                .replaceAll("\\n{3,}", "\\n\\n")
                .trim();
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonMap(String content) throws Exception {
        String trimmed = content == null ? "" : content.trim();
        try {
            return objectMapper.readValue(trimmed, Map.class);
        } catch (Exception first) {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String json = trimmed.substring(start, end + 1);
                return objectMapper.readValue(json, Map.class);
            }
            throw first;
        }
    }

    // ── PDF → Base64 data URL list ──────────────────────────────────────────

    private List<String> pdfToDataUrls(byte[] fileBytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(new ByteArrayInputStream(fileBytes)))) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int pageCount = Math.min(doc.getNumberOfPages(), maxPages);
            List<String> urls = new ArrayList<>();
            for (int i = 0; i < pageCount; i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, renderDpi, ImageType.RGB);
                byte[] jpeg = toJpegBytes(img);
                urls.add("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg));
                log.debug("Rendered PDF page {} → {} KB JPEG", i + 1, jpeg.length / 1024);
            }
            if (doc.getNumberOfPages() > maxPages) {
                log.warn("PDF has {} pages, truncated to {}", doc.getNumberOfPages(), maxPages);
            }
            return urls;
        }
    }

    // ── Image utilities ─────────────────────────────────────────────────────

    private byte[] toJpegBytes(BufferedImage image) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) throw new IllegalStateException("JPG writer not available");
        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(Math.max(0.1f, Math.min(1.0f, imageQuality)));
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    private String detectMimeType(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".bmp")) return "image/bmp";
        return "image/jpeg";
    }
}
