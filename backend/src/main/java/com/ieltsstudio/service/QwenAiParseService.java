package com.ieltsstudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieltsstudio.ai.AiFeature;
import com.ieltsstudio.ai.AiProviderType;
import com.ieltsstudio.ai.AiTaskType;
import com.ieltsstudio.ai.client.OpenAiCompatibleClient;
import com.ieltsstudio.ai.model.AiChatMessage;
import com.ieltsstudio.ai.model.AiChatRequest;
import com.ieltsstudio.ai.model.AiChatResponse;
import com.ieltsstudio.ai.model.AiCredentials;
import com.ieltsstudio.ai.service.AiSettingsService;
import com.ieltsstudio.ai.service.AiUsageGuard;
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

    /** BUILTIN 默认 provider 名（仅用于日志展示，真实 provider 由 AiSettingsService 按用户解析） */
    @Value("${ai.precise.provider:qwen}")
    private String provider;

    @Value("${qwen.max-pages:20}")
    private int maxPages;

    @Value("${qwen.render-dpi:160}")
    private int renderDpi;

    @Value("${qwen.image-quality:0.82}")
    private float imageQuality;

    @Value("${qwen.ai-max-tokens:8192}")
    private int aiMaxTokens;

    @Value("${mimo.ai-max-tokens:8192}")
    private int mimoMaxTokens;

    @Value("${ai.precise.http-timeout-seconds:240}")
    private int httpTimeoutSeconds;

    private final ObjectMapper objectMapper;
    private final AiSettingsService aiSettingsService;
    private final AiUsageGuard aiUsageGuard;
    private final OpenAiCompatibleClient openAiCompatibleClient;

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
            - 【极其重要】如果填空题要求从方框词库/选项列表中选择（如 A-Q: A cost, B falling, C technology...），
              每道题都必须包含 options JSON对象：{"A":"cost","B":"falling","C":"technology",...}
            - 【极其重要】这类题仍然可以标记为 "fill"，但 options 绝不能只写 ["A","B","C"]，必须写完整词语。

        【重要】题型判定规则：
        - 如果题目要求从一个选项列表（如 A-P、A-H 等）中选择答案，必须标记为 "mcq"，并在 options 中列出所有可选项。
        - options 必须是 JSON 对象格式，每个选项值必须是完整的选项描述文字（不能只写字母标识）。
          例如：{"A":"Rainforests are in danger.","B":"Climate change is the main threat.","C":"..."}
        - 如果一组题目共享同一个选项列表（如 Questions 9-13 从 A-P 中选），每道题的 options 都必须包含完整选项列表。
        - 如果填空题带有词库框（如 A cost / B falling / C technology / ... / Q independent），每道填空题也必须包含完整 options 对象。
          示例：{"A":"cost","B":"falling","C":"technology","D":"undernourished","E":"earlier","F":"later"}
        - 只有答案是从文章中提取的单词或短语时才使用 "fill"。
        - 选项列表可能出现在一组题目的前面或后面，需要关联到对应的题目。
        - 匹配题（matching）也属于 "mcq"，需要将匹配列表作为 options 提供。

        规则：
        - 【极其重要】对于阅读部分：所有问题都放入 questions 数组，passages 数组中【绝对不能包含任何题目】。
        - 【极其重要】正文（passages）只包含文章/阅读段落内容，题目必须全部放入 questions 数组。
        - 保留原始问题编号（1, 2, 3...）。
        - 【极其重要】不得跳过任何题号。如果题目指示为 Questions 1-5 或 Questions 1-13，则必须输出每一道题，不得遗漏。
        - 【极其重要】对于标题匹配题（heading matching），如果文章中有 A、B、C、D、E 等标注段落，则必须为每个段落都生成一道题目（text 为 "Paragraph A"、"Paragraph B" 等），不能只生成其中一道。
        - 【极其重要】options 绝不能写成范围字符串如 "i-vii"、"A-F"！必须展开为完整对象，例如 {"i":"Avoiding...","ii":"A successful..."}。
        - 【极其重要】locatorText 必须从 passage 中原样复制，即使有 OCR 拼写错误也保留原样，绝不能编造。
        - answer：如果答案区域有答案则复制；否则从 passage 中推导。
        - locatorText：来自 passage 的【短小原词短语（3-8个词）】，不可为空。
        - 只返回 JSON 对象，不要用 markdown 代码块包裹，不要输出任何额外文字。
        """;
    
    private static final String WRITING_PROMPT = """
        你是一个具备视觉能力的雅思写作试卷解析器。返回一个 JSON 对象。

        ── PASSAGES ────────────────────────────────────────────────────────────
        “passages”：字符串数组，每个写作任务（Task 1、Task 2）一个条目。

        对于 TASK 2（议论文）：必须逐字包含所有考试指令文字，例如 "WRITING TASK 2"、"You should spend about 40 minutes on this task."、"Write about the following topic:"、"Write at least 250 words." 等。不要省略任何原文中的文字。不需要特殊区块。

        对于 TASK 1（描述图表）：passage 字符串必须【按顺序】包含以下区块，区块外不得有任何额外文字；
        特别要求：必须包含完整且格式严格的“[Visual Data Summary]”区块。
        - 如果页面上存在“多个独立图表”（例如上下两张图），必须将每一张图单独记录为一组元数据：
          在 [Visual Data Summary] 内，对每张图分别给出一段 `chartTitle:`、`chartType:`、`xAxis:`、`yAxis:`、`series:` 与数据行；
          多张图之间用一个空行分隔；严禁把多张图的数据混在一起。
        - 若无法识别到可视数据，也应明确输出 chartType 行并如实给出可提取的数据点；
        严禁省略该区块或以其他标签/字段名替代。一旦未满足，视为不合格输出，应重新生成满足规范的 JSON。

        【结构化输出要求】在保证 passages 包含上述文本块的同时，必须直接提供结构化字段：
        - charts: 数组。若无图表则返回 []。如果页面上有多张图，charts 必须包含多个元素，每个元素只对应一张图（禁止把两张图合并）。
          每个元素为 { title, type: "bar"|"pie"|"line", unit: "percent"|"million"|"thousand"|"count"|null, categories: [字符串],
          series: [{ name: 字符串, data: [{ label: 字符串, value: 数值 }], color: 字符串|null }], yAxisRange: { min: 数值|null, max: 数值|null }|null }
          其中 color（可选）记录图例/配色（如 "#2f7ed8"、"green"、"dark gray"）；若无法可靠识别则置 null，但必须根据图例/颜色把不同系列拆分为不同的 series.name。
        - tables: 数组。若无表格则返回 []，否则每个元素为 { title: 字符串|null, headers: [字符串...], rows: [[字符串...], ...] }
        - 数值行必须独立，禁止把多条数据合并到同一行，禁止输出 keyValues/keyCategories 等自定义字段。
        - charts.categories 与 data.label 必须一致，series.data.value 必须是数字，不能为空；type 只能是 bar/pie/line 之一。
        - 必须保持原图的数值与刻度，不得归一化/比例化/四舍五入；负号必须保留。若能识别纵轴刻度范围，请填写 yAxisRange.min/max；无法识别则置 null。
        - 若信息缺失，用空数组或 null 表示，不要编造文字描述。
        - 示例（仅展示结构，切勿照抄数据）：
          "charts": [
            {
              "title": "Sample",
              "type": "bar",
              "unit": "percent",
              "categories": ["A","B"],
              "series": [
                {"name": "2020", "data": [{"label": "A", "value": 40}, {"label": "B", "value": 60}]},
                {"name": "2021", "data": [{"label": "A", "value": 35}, {"label": "B", "value": 65}]}
              ]
            }
          ],
          "tables": [
            {"title": "Sample Table", "headers": ["Col1","Col2"], "rows": [["r1c1","r1c2"], ["r2c1","r2c2"]]}
          ]

          [Task Prompt]
          <逐字复制完整题目指令，例如“The chart below shows...
           Summarise the information by selecting and reporting the main features...”>

          [Visual Data Summary]
          chartTitle: <图表的主标题，如果有的话>
          chartType: <以下之一：pie chart | bar chart | line graph | table | map | process diagram>
          xAxis: <横轴维度：year | category>
          yAxis: <纵轴单位：percent | million | thousand | count>
          series: <系列维度：对于计数图=类别（如“Marriages|Divorces”）；对于百分比图=年份（如“1970|2000”）>
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
        - 明确坐标与系列（需要区分不同颜色/图例）：
          • 当为计数类（如 million/thousand/count）→ xAxis=year，series=类别（Marriages|Divorces），yAxis=对应单位（如 million）
          • 当为百分比类（percent）→ xAxis=category，series=年份（1970|2000），yAxis=percent
        - 不得把不同颜色（不同图例）代表的数值写入同一个 series；每种颜色/图例必须对应一个独立的 series.name。
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
            wordLimit       : 整数，从原文提取字数要求；若原文说 "80-100 words" 则为 80；若未明确则 Task1=150，Task2=250
            text            : 与 passages 中该任务的完整原文相同（包含标题、时间提示、话题引导、题目正文、字数要求等所有内容，不得省略）
            answer          : 建议的写作思路/关键点，≤60 字
            explanation     : 一句话说明高分作文应覆盖哪些要点
            locatorText     : 来自题目提示文字的【原词短语（4–8 个词）】

        ── 规则 ───────────────────────────────────────────────────────────────
        - 如果 Task 1 和 Task 2 同时出现，则 passages[] 中包含两个条目，questions[] 中也包含两个对象。
        - locatorText 必须来自题目提示文字，不得自行编造。
        - 关键：不要修改、改动或重写原图中的任何文字。逐字复制所有文本，包括题目指令、图表标题、标签和表格内容。不允许总结或转述。
        - 只返回 JSON 对象 —— 不要用 markdown 代码块包裹，不要输出任何额外文字。
        - 严格校验：Task 1 的 passage 中必须出现“[Visual Data Summary]”与“chartType:”独立行；否则此回答不合格。

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
                如果填空题带有方框词库/选项列表（如 A-Q: A cost, B falling...），必须额外包含完整 options 对象 {"A":"cost","B":"falling",...}

            【重要】题型判定规则：
            - 如果题目要求从一个选项列表（如 A-P、A-H 等）中选择答案，必须标记为 "mcq"，并在 options 中列出所有可选项。
            - 如果填空题要求从方框词库（如 A-Q）中选择答案，仍可标记为 "fill"，但每道题必须包含完整 options 对象，不能只写字母。
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

    public String getProviderName() {
        return "mimo".equalsIgnoreCase(provider) ? "MiMO" : "Qwen";
    }

    /**
     * @deprecated 新架构下请使用 {@link #isConfigured(Long)}，由 {@link AiSettingsService} 按用户解析 Vision 凭据。
     *             保留该方法仅为兼容极少 legacy 测试，主链路不应再调用。
     */
    @Deprecated
    public boolean isConfigured() {
        return false;
    }

    /**
     * 判断当前用户是否已配置 Vision Provider（BUILTIN 或 USER 模式任一）。
     * 由 {@link AiSettingsService#resolve(Long, AiTaskType)} 决定：解析成功即视为已配置。
     */
    public boolean isConfigured(Long userId) {
        try {
            aiSettingsService.resolve(userId, AiTaskType.VISION);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Public entry point ──────────────────────────────────────────────────

    /**
     * Parse a PDF or image file directly into structured exam JSON using Vision provider.
     * Returns a Map with either {"passages":...,"questions":...} or {"sections":[...]}.
     *
     * @param userId   登录用户 ID，用于解析 Vision Provider 凭据
     * @param fileBytes The file content as bytes
     * @param filename The original filename
     * @param examType The exam type (reading/writing/listening), defaults to "reading"
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseDocument(Long userId, byte[] fileBytes, String filename, String examType) throws Exception {
        AiCredentials credentials = aiSettingsService.resolve(userId, AiTaskType.VISION);
        log.info("Precise parse using provider={}, model={}, baseUrl={}",
                credentials.getProvider(), credentials.getModel(), credentials.getBaseUrl());
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
            throw new RuntimeException("精准解析当前仅支持 PDF 或图片文件");
        }

        if (dataUrls.size() == 1) {
            return parseSingleImage(userId, credentials, dataUrls.get(0), examType);
        } else {
            return parseMultiPageImages(userId, credentials, dataUrls, examType);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> parseImages(Long userId, List<byte[]> imageBytes, List<String> filenames, String examType) throws Exception {
        AiCredentials credentials = aiSettingsService.resolve(userId, AiTaskType.VISION);
        log.info("Precise parse(images) using provider={}, model={}, baseUrl={}",
                credentials.getProvider(), credentials.getModel(), credentials.getBaseUrl());
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
            return parseSingleImage(userId, credentials, dataUrls.get(0), examType);
        }
        return parseMultiPageImages(userId, credentials, dataUrls, examType);
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
    private Map<String, Object> parseSingleImage(Long userId, AiCredentials credentials,
                                                  String dataUrl, String examType) throws Exception {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text",
                "请仔细阅读这张IELTS试卷页面图片中的所有文字、表格、图表，然后返回结构化JSON："));
        content.add(Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)));

        String prompt = getPromptForExamType(examType, false);
        return callVisionProvider(userId, credentials, prompt, content);
    }

    // ── Multi-page → structured JSON with sections ──────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMultiPageImages(Long userId, AiCredentials credentials,
                                                      List<String> dataUrls, String examType) throws Exception {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text",
                "以下是同一份IELTS试卷的多页图片，请整体阅读所有页面中的文字、表格、图表，" +
                "检测试题分区并分别解析，返回结构化JSON："));
        for (String url : dataUrls) {
            content.add(Map.of("type", "image_url", "image_url", Map.of("url", url)));
        }

        String prompt = getPromptForExamType(examType, true);
        Map<String, Object> parsed = callVisionProvider(userId, credentials, prompt, content);

        // Normalise: if AI returned flat passages/questions instead of sections wrapper, wrap it.
        // 该 wrapping 属于业务后处理，不在 markSuccess/markFailure 的 try-catch 边界内。
        if (!parsed.containsKey("sections") && parsed.containsKey("questions")) {
            Map<String, Object> section = new LinkedHashMap<>(parsed);
            section.putIfAbsent("name", "");
            section.putIfAbsent("type", "reading");
            parsed = Map.of("sections", List.of(section));
        }
        return parsed;
    }

    // ── Vision Provider 调用（新架构） ───────────────────────────────────────

    /**
     * 统一 Vision Provider 调用：{@link AiSettingsService} 已解析好 {@link AiCredentials}，
     * 这里只负责 {@link AiUsageGuard} 用量守卫 + {@link OpenAiCompatibleClient} 发请求 +
     * JSON 解析成功后 markSuccess / 失败 markFailure + 异常脱敏。
     *
     * <p>返回值是已经过 {@link #normalizeParsedResult} 的 Map，避免调用方重复处理。</p>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callVisionProvider(Long userId,
                                                    AiCredentials credentials,
                                                    String systemPrompt,
                                                    List<Map<String, Object>> userContent) throws Exception {
        int maxTokens = resolveVisionMaxTokens(credentials);
        int timeoutSeconds = Math.max(60, httpTimeoutSeconds);
        String provider = providerName(credentials);
        aiUsageGuard.checkBeforeCall(userId, AiFeature.EXAM_PRECISE_PARSE, credentials.getKeyMode(), provider);
        try {
            AiChatResponse response = openAiCompatibleClient.chat(AiChatRequest.builder()
                    .credentials(credentials)
                    .messages(List.of(
                            AiChatMessage.system(systemPrompt),
                            AiChatMessage.userMultimodal(userContent)))
                    .maxTokens(maxTokens)
                    .temperature(0.1)
                    .jsonMode(true)
                    .timeoutSeconds(timeoutSeconds)
                    .build());
            String content = stripCodeFence(response.getContent());
            Map<String, Object> parsed = readJsonMap(content);
            Map<String, Object> normalized = normalizeParsedResult(parsed);
            aiUsageGuard.markSuccess(userId, AiFeature.EXAM_PRECISE_PARSE, credentials.getKeyMode(), provider);
            return normalized;
        } catch (Exception ex) {
            aiUsageGuard.markFailure(userId, AiFeature.EXAM_PRECISE_PARSE, credentials.getKeyMode(), provider, ex);
            throw aiCallFailed(ex);
        }
    }

    /**
     * 根据 provider 决定 vision 任务的 maxTokens。
     * USER 模式下自定义 provider（OPENAI_COMPATIBLE）默认使用 {@link #aiMaxTokens}。
     */
    private int resolveVisionMaxTokens(AiCredentials credentials) {
        if (credentials.getProvider() == AiProviderType.MIMO) {
            return mimoMaxTokens;
        }
        return aiMaxTokens;
    }

    private String stripCodeFence(String content) {
        if (content == null) return "";
        if (content.startsWith("```")) {
            content = content.replaceAll("^```[a-z]*\\n?", "").replaceAll("\\n?```$", "").trim();
        }
        return content;
    }

    /**
     * Vision 调用失败时构造脱敏异常。不输出 ex.getMessage()（可能含 provider body），
     * 不返回原始 body / Key / Authorization 给调用方。
     */
    private RuntimeException aiCallFailed(Exception ex) {
        log.warn("Vision AI 调用失败: {}", ex.getClass().getSimpleName());
        return new RuntimeException("AI 精准解析暂时不可用，请稍后重试");
    }

    /** 仅返回 provider 枚举名或 null，避免在 usage record 中暴露 baseUrl / model / API Key */
    private static String providerName(AiCredentials credentials) {
        return credentials.getProvider() == null ? null : credentials.getProvider().name();
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
                    enforceVisualDataSummary(section);
                }
            }
        } else {
            normalizePassages(parsed);
            normalizeQuestions(parsed);
            enforceVisualDataSummary(parsed);
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

    /**
     * Ensure Task 1 passages contain a [Visual Data Summary] block. If missing, try to
     * synthesize it from detected chartTitle/chartType lines and data point lines.
     * This does not modify non-writing passages.
     */
    @SuppressWarnings("unchecked")
    private void enforceVisualDataSummary(Map<String, Object> node) {
        Object pObj = node.get("passages");
        if (!(pObj instanceof List<?> pList) || pList.isEmpty()) return;

        List<String> updated = new ArrayList<>();
        for (Object p : pList) {
            String text = p == null ? "" : String.valueOf(p);
            String norm = text.replace("\r\n", "\n").replace("\r", "\n");

            // Heuristic: if already contains the tag, keep as-is
            if (norm.toLowerCase(Locale.ROOT).contains("[visual data summary]".toLowerCase(Locale.ROOT))) {
                updated.add(text);
                continue;
            }

            // Try to detect chartType/chartTitle lines and data points
            String[] lines = norm.split("\n");
            String chartTitle = null;
            String chartType = null;
            List<String> dataLines = new ArrayList<>();
            for (String raw : lines) {
                String line = raw == null ? "" : raw.trim();
                if (line.isEmpty()) continue;
                if (chartTitle == null && line.matches("(?i)^chartTitle\s*[:：].+")) {
                    chartTitle = line.replaceFirst("(?i)^chartTitle\s*[:：]\\s*", "").trim();
                    continue;
                }
                if (chartType == null && line.matches("(?i)^chartType\s*[:：].+")) {
                    chartType = line.replaceFirst("(?i)^chartType\s*[:：]\\s*", "").trim();
                    continue;
                }
                // Data line: Label: number [unit]
                if (line.matches("^.{1,100}:\\s*-?\\d+(?:\\.\\d+)?(?:\\s*(%|percent|percentage|million|millions|billion|billions|thousand|thousands|hundred|hundreds|k|m|bn|people|adults|students|cases|units|items|cars|vehicles))?$")) {
                    dataLines.add(line);
                }
            }

            boolean looksLikeTask1 = chartType != null || dataLines.size() >= 2 || norm.matches("(?is).*The (chart|graph|table) below shows.*");
            if (looksLikeTask1 && (chartType != null || !dataLines.isEmpty())) {
                StringBuilder block = new StringBuilder();
                block.append("[Visual Data Summary]\n");
                if (chartTitle != null && !chartTitle.isBlank()) {
                    block.append("chartTitle: ").append(chartTitle).append('\n');
                }
                if (chartType != null && !chartType.isBlank()) {
                    block.append("chartType: ").append(chartType).append('\n');
                }
                for (String dl : dataLines) block.append(dl).append('\n');

                String synthesized = block.toString().trim();
                String newText = synthesized + "\n\n" + text;
                updated.add(newText);
                log.info("Synthesized [Visual Data Summary] block ({} data lines) into passage.", dataLines.size());
            } else {
                updated.add(text);
            }
        }

        node.put("passages", updated);
    }

    @SuppressWarnings("unchecked")
    private void normalizeQuestions(Map<String, Object> node) {
        Object qObj = node.get("questions");
        if (!(qObj instanceof List<?> qList)) return;
        // Determine whether passage explicitly contains a WRITING TASK 2 marker
        String joinedPassages = "";
        Object pObj = node.get("passages");
        if (pObj instanceof List<?> pList) {
            List<String> ps = new ArrayList<>();
            for (Object p : pList) if (p != null) ps.add(String.valueOf(p));
            joinedPassages = String.join("\n", ps);
        }
        boolean hasTask2Marker = Pattern.compile("(?i)WRITING\\s+TASK\\s*2").matcher(joinedPassages).find();

        List<Map<String, Object>> cleaned = new ArrayList<>();
        for (Object q : qList) {
            if (!(q instanceof Map<?, ?>)) continue;
            Map<String, Object> qm = (Map<String, Object>) q;

            // Optionally drop spurious Task2 when passage lacks explicit marker
            Object type = qm.get("type");
            Object taskType = qm.get("taskType");
            if (!hasTask2Marker && "write".equals(type) && taskType != null &&
                String.valueOf(taskType).equalsIgnoreCase("Task2")) {
                continue; // skip synthetic Task2
            }

            Object text = qm.get("text");
            if (text != null) {
                String norm = normalizePassageText(String.valueOf(text));
                qm.put("text", stripVisualBlocksFromText(norm));
            }
            Object locator = qm.get("locatorText");
            if (locator != null) qm.put("locatorText", String.valueOf(locator).replaceAll("\\s+", " ").trim());

            cleaned.add(qm);
        }
        node.put("questions", cleaned);
    }

    /**
     * Remove visual/table blocks and inline chart/table lines from question text to keep prompt clean.
     */
    private String stripVisualBlocksFromText(String input) {
        if (input == null || input.isBlank()) return "";
        String t = input.replace("\r\n", "\n").replace("\r", "\n");
        // Remove [Visual Data Summary] and [Table Data] blocks
        t = t.replaceAll("(?is)\n?\\[Visual Data Summary\\][\\s\\S]*?(?=\n\\[[^\\]]+\\]|$)", "\n");
        t = t.replaceAll("(?is)\n?\\[Table Data\\][\\s\\S]*?(?=\n\\[[^\\]]+\\]|$)", "\n");
        // Remove typical inline chart meta lines and numeric data rows
        List<String> out = new ArrayList<>();
        for (String raw : t.split("\\n")) {
            String line = raw == null ? "" : raw.trim();
            if (line.matches("(?i)^(chartTitle|chartType|xAxis|yAxis|series)\\s*[:：].*")) continue;
            if (line.matches("^.{1,100}:\\s*-?\\d+(?:\\.\\d+)?(?:\\s*(%|percent|percentage|million|millions|billion|billions|thousand|thousands|hundred|hundreds|k|m|bn|people|adults|students|cases|units|items|cars|vehicles))?$")) continue;
            out.add(raw);
        }
        return String.join("\n", out).replaceAll("\n{3,}", "\n\n").trim();
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
