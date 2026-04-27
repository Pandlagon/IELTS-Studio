package com.ieltsstudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieltsstudio.entity.Exam;
import com.ieltsstudio.entity.Question;
import com.ieltsstudio.mapper.ExamMapper;
import com.ieltsstudio.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncParseService {

    private final ExamMapper examMapper;
    private final QuestionMapper questionMapper;
    private final FileParseService fileParseService;
    private final AiParseService aiParseService;
    private final QwenAiParseService qwenAiParseService;
    private final ObjectMapper objectMapper;

    private static final Pattern QUESTION_START_MARKER = Pattern.compile(
            "(?im)^\\s*(questions?\\s*\\d+\\s*[-–]\\s*\\d+|questions?\\s*\\d+|q\\s*\\d+\\s*[-–]\\s*\\d+|q\\s*\\d+|\\d+\\s+[A-Za-z(])");

    private static final Pattern INSTRUCTION_LINE = Pattern.compile(
            "(?im)^\\s*(complete\\s+the\\s+notes\\s+below\\.?|complete\\s+the\\s+sentences\\s+below\\.?|choose\\s+no\\s+more\\s+than\\s+\\w+\\s+words?\\s+from\\s+the\\s+passage.*|choose\\s+one\\s+word\\s+only.*|choose\\s+two\\s+words?\\s+only.*|choose\\s+three\\s+words?\\s+only.*|write\\s+your\\s+answers?\\s+in\\s+boxes.*|in\\s+boxes?\\s+\\d+\\s*[-–]\\s*\\d+.*|in\\s+boxes?\\s+\\d+.*)$");

    private static final Pattern ANSWER_WORD_LIMIT = Pattern.compile(
            "(?i)(ONE|TWO|THREE|FOUR|FIVE|SIX|SEVEN|EIGHT|NINE|TEN)\\s+WORDS?\\s+ONLY|NO\\s+MORE\\s+THAN\\s+(\\d+)\\s+WORDS?",
            Pattern.CASE_INSENSITIVE);

    private static final Map<String, Integer> WORD_NUM = Map.of(
            "ONE", 1,
            "TWO", 2,
            "THREE", 3,
            "FOUR", 4,
            "FIVE", 5,
            "SIX", 6,
            "SEVEN", 7,
            "EIGHT", 8,
            "NINE", 9,
            "TEN", 10
    );

    @Async
    public void parseAndSave(Long examId, byte[] fileBytes, String originalFilename,
                             boolean parsePrecise, String extractedText, String examType) {
        try {
            if (parsePrecise && qwenAiParseService.isConfigured()) {
                // ── Precise path: Qwen vision → structured JSON directly ───────────────
                log.info("Exam {} – using Qwen AI precise parse (vision → JSON) for type: {}", examId, examType);
                Map<String, Object> parsed;
                try {
                    parsed = qwenAiParseService.parseDocument(fileBytes, originalFilename, examType);
                } catch (Exception qwenEx) {
                    log.warn("Exam {} – Qwen AI precise parse failed ({}), falling back to text extraction + DeepSeek",
                            examId, qwenEx.getMessage());
                    String fallbackText = fileParseService.extractTextFromBytes(fileBytes, originalFilename);
                    handleMultiSection(examId, fallbackText);
                    return;
                }
                handleQwenParsedResult(examId, parsed);
            } else {
                // ── Standard path ─────────────────────────────────────────────
                String text;
                if (extractedText != null && extractedText.trim().length() >= 80) {
                    // Use client-side pdf.js extraction (skips PDFBox entirely)
                    log.info("Exam {} – using client-extracted text ({} chars)", examId, extractedText.trim().length());
                    text = extractedText;
                } else {
                    text = fileParseService.extractTextFromBytes(fileBytes, originalFilename);
                }
                
                // If text extraction failed and Qwen AI is available, auto-switch to precise parse
                if (text == null || text.trim().length() < 80) {
                    if (qwenAiParseService.isConfigured()) {
                        log.warn("Exam {} – extraction too short ({} chars), auto-retrying with Qwen AI precise parse for type: {}",
                                examId, text == null ? 0 : text.trim().length(), examType);
                        try {
                            Map<String, Object> qwenResult = qwenAiParseService.parseDocument(fileBytes, originalFilename, examType);
                            handleQwenParsedResult(examId, qwenResult);
                            return;
                        } catch (Exception qwenEx) {
                            log.error("Exam {} – Qwen AI precise parse also failed", examId, qwenEx);
                            throw new RuntimeException("文字提取失败且精准解析也无法处理，请上传清晰的PDF或Word文件");
                        }
                    } else {
                        throw new RuntimeException("提取文字过少（" + (text == null ? 0 : text.trim().length())
                                + " 字符），可能是扫描版PDF，请使用精准解析或上传Word格式");
                    }
                }
                
                // Use workflow parse (Step1 structure + Step2 per-group answers)
                Map<String, Object> parsed = workflowParse(examId, text);
                commitSection(examId, null, parsed, null, text);
            }
        } catch (Exception e) {
            log.error("Failed to parse exam {}", examId, e);
            markError(examId);
        }
    }

    @Async
    public void parseAndSaveImages(Long examId, List<byte[]> imageBytes, List<String> filenames,
                                   boolean parsePrecise, String extractedText, String examType) {
        try {
            if (parsePrecise) {
                if (!qwenAiParseService.isConfigured()) {
                    throw new RuntimeException("精准解析未配置（Qwen API Key 缺失），无法解析图片");
                }
                Map<String, Object> parsed = qwenAiParseService.parseImages(imageBytes, filenames, examType);
                handleQwenParsedResult(examId, parsed);
                return;
            }

            // Non-precise multi-image flow: rely on client-side OCR text.
            if (extractedText == null || extractedText.trim().length() < 80) {
                throw new RuntimeException("图片普通解析需要前端 OCR 提取文字（extractedText 过短）");
            }
            Map<String, Object> parsed = workflowParse(examId, extractedText);
            commitSection(examId, null, parsed, null, extractedText);
        } catch (Exception e) {
            log.error("Failed to parse exam {} (images)", examId, e);
            markError(examId);
        }
    }

    /**
     * Legacy method for backward compatibility - defaults to reading type
     */
    @Async
    public void parseAndSave(Long examId, byte[] fileBytes, String originalFilename,
                             boolean parsePrecise, String extractedText) {
        parseAndSave(examId, fileBytes, originalFilename, parsePrecise, extractedText, "reading");
    }

    // ── Qwen AI direct parse result handling ────────────────────────────────────────────────

    /**
     * Handle the structured JSON returned by QwenAiParseService.
     * The result may contain {"sections":[...]} or flat {"passages":[...],"questions":[...]}.
     */
    @SuppressWarnings("unchecked")
    private void handleQwenParsedResult(Long examId, Map<String, Object> parsed) throws Exception {
        Exam original = examMapper.selectById(examId);

        if (parsed.containsKey("sections")) {
            // Multi-section: merge all sections into one exam
            List<Map<String, Object>> sections = (List<Map<String, Object>>) parsed.get("sections");
            List<String> allPassages = new ArrayList<>();
            List<Map<String, Object>> allQuestions = new ArrayList<>();

            for (Map<String, Object> section : sections) {
                List<String> p = (List<String>) section.get("passages");
                List<Map<String, Object>> q = (List<Map<String, Object>>) section.get("questions");
                if (p != null) allPassages.addAll(p);
                if (q != null) allQuestions.addAll(q);
            }

            // Sort and deduplicate
            allQuestions.sort(Comparator.comparingInt(q -> {
                Object num = q.get("questionNumber");
                return num instanceof Number ? ((Number) num).intValue() : 999;
            }));
            Map<Integer, Map<String, Object>> deduped = new LinkedHashMap<>();
            for (Map<String, Object> q : allQuestions) {
                Object num = q.get("questionNumber");
                int n = num instanceof Number ? ((Number) num).intValue() : 999;
                deduped.putIfAbsent(n, q);
            }

            Map<String, Object> merged = new LinkedHashMap<>();
            merged.put("passages", allPassages);
            merged.put("questions", new ArrayList<>(deduped.values()));

            boolean anyWrite = allQuestions.stream().anyMatch(q -> "write".equals(q.get("type")));
            if (anyWrite) original.setType("writing");

            commitSection(examId, null, merged, original);
            log.info("Exam {} – Qwen AI parsed {} section(s), {} unique questions",
                    examId, sections.size(), deduped.size());
        } else {
            // Single section result
            commitSection(examId, null, parsed, original);
            List<Map<String, Object>> questions = (List<Map<String, Object>>) parsed.get("questions");
            log.info("Exam {} – Qwen AI parsed single section, {} questions",
                    examId, questions != null ? questions.size() : 0);
        }
    }

    // ── Multi-section handling (text-based DeepSeek flow) ──────────────────────────────

    /**
     * Split raw text into sections by common IELTS markers.
     * Each returned chunk contains one passage + its questions.
     */
    private List<String> splitBySectionMarkers(String text) {
        Pattern p = Pattern.compile(
            "(?im)^(reading passage \\d+|section \\d+|part \\d+|writing task \\d+|listening section \\d+)",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        List<Integer> cuts = new ArrayList<>();
        while (m.find()) cuts.add(m.start());

        if (cuts.isEmpty()) return List.of(text);

        List<String> result = new ArrayList<>();
        // text before first marker (might be preamble; skip if tiny)
        if (cuts.get(0) > 300) result.add(text.substring(0, cuts.get(0)).trim());
        for (int i = 0; i < cuts.size(); i++) {
            int end = (i + 1 < cuts.size()) ? cuts.get(i + 1) : text.length();
            String chunk = text.substring(cuts.get(i), end).trim();
            if (chunk.length() > 150) result.add(chunk);
        }
        return result.isEmpty() ? List.of(text) : result;
    }

    @SuppressWarnings("unchecked")
    private void handleMultiSection(Long examId, String text) throws Exception {
        Exam original = examMapper.selectById(examId);

        // Split into section chunks and parse each independently
        List<String> chunks = splitBySectionMarkers(text);
        log.info("Exam {} – split into {} chunk(s) for independent parsing", examId, chunks.size());

        List<String> allPassages = new ArrayList<>();
        List<Map<String, Object>> allQuestions = new ArrayList<>();
        int parsedChunks = 0;

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            try {
                Map<String, Object> parsed = aiParseService.parseWithAi(chunk);
                List<String> passages = (List<String>) parsed.get("passages");
                List<Map<String, Object>> questions = (List<Map<String, Object>>) parsed.get("questions");
                if (passages != null) allPassages.addAll(passages);
                if (questions != null) allQuestions.addAll(questions);
                parsedChunks++;
                log.info("Exam {} – chunk {}/{}: {} questions", examId, i + 1, chunks.size(),
                        questions == null ? 0 : questions.size());
            } catch (Exception e) {
                log.warn("Exam {} – chunk {} parse failed: {}", examId, i + 1, e.getMessage());
            }
        }

        if (parsedChunks == 0) {
            // All chunks failed – fall back to workflow parse
            log.warn("Exam {} – all chunks failed, falling back to workflow parse", examId);
            Map<String, Object> parsed = workflowParse(examId, text);
            commitSection(examId, null, parsed, original, text);
            return;
        }

        // Sort by original questionNumber (keep PDF numbering, no offset added)
        allQuestions.sort(Comparator.comparingInt(q -> {
            Object num = ((Map<String, Object>) q).get("questionNumber");
            return num instanceof Number ? ((Number) num).intValue() : 999;
        }));

        // Deduplicate by questionNumber (keep first occurrence)
        Map<Integer, Map<String, Object>> deduped = new LinkedHashMap<>();
        for (Map<String, Object> q : allQuestions) {
            Object num = q.get("questionNumber");
            int n = num instanceof Number ? ((Number) num).intValue() : 999;
            deduped.putIfAbsent(n, q);
        }

        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("passages", allPassages);
        merged.put("questions", new ArrayList<>(deduped.values()));

        boolean anyWrite = allQuestions.stream().anyMatch(q -> "write".equals(q.get("type")));
        original.setType(anyWrite ? "writing" : original.getType());

        commitSection(examId, null, merged, original, text);
        log.info("Exam {} merged {} chunk(s) – {} unique questions", examId, parsedChunks, deduped.size());
    }

    // ── Single-section AI parse ───────────────────────────────────────────────

    private Map<String, Object> parseSingle(Long examId, String text) throws Exception {
        if (text == null || text.trim().length() < 80) {
            throw new RuntimeException("提取文字过少（" + (text == null ? 0 : text.trim().length())
                    + " 字符），可能是扫描版PDF，请使用精准解析或上传Word格式");
        }
        if (aiParseService.isConfigured()) {
            try {
                Map<String, Object> p = aiParseService.parseWithAi(text);
                log.info("Exam {} – AI parse succeeded", examId);
                return p;
            } catch (Exception aiEx) {
                log.warn("Exam {} – AI parse failed ({}), falling back to regex", examId, aiEx.getMessage());
            }
        } else {
            log.info("Exam {} – AI not configured, using regex parser", examId);
        }
        return fileParseService.parseExamContent(text);
    }

    // ── Workflow parse: Step1 structure + Step2 per-group answers ──────────────

    /**
     * Two-step workflow parse using DeepSeek:
     *   Step 1: Extract passages + question group skeleton (type, range, options, question texts)
     *   Step 2: For each group, call AI to generate answers + explanations
     * Falls back to parseSingle if workflow fails or DeepSeek is not configured.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> workflowParse(Long examId, String text) throws Exception {
        if (text == null || text.trim().length() < 80) {
            throw new RuntimeException("提取文字过少（" + (text == null ? 0 : text.trim().length())
                    + " 字符），可能是扫描版PDF，请使用精准解析或上传Word格式");
        }

        if (!aiParseService.isConfigured()) {
            log.info("Exam {} – AI not configured, using regex parser", examId);
            return fileParseService.parseExamContent(text);
        }

        try {
            // ── Step 1: Structure extraction ──
            log.info("Exam {} – Workflow Step1: extracting structure...", examId);
            Map<String, Object> step1 = aiParseService.workflowStep1(text);

            List<String> passages = (List<String>) step1.get("passages");
            List<Map<String, Object>> groups = (List<Map<String, Object>>) step1.get("questionGroups");

            if (groups == null || groups.isEmpty()) {
                log.warn("Exam {} – Workflow Step1 returned 0 groups, falling back to parseSingle", examId);
                return parseSingle(examId, text);
            }

            log.info("Exam {} – Workflow Step1: {} passages, {} question groups", examId,
                    passages == null ? 0 : passages.size(), groups.size());

            // ── Step 2: Per-group answer generation ──
            String passageText = (passages != null && !passages.isEmpty())
                    ? String.join("\n\n", passages) : "";
            List<Map<String, Object>> allQuestions = new ArrayList<>();

            for (int i = 0; i < groups.size(); i++) {
                Map<String, Object> group = groups.get(i);
                String range = String.valueOf(group.getOrDefault("range", "group" + (i + 1)));
                try {
                    Map<String, Object> step2Result = aiParseService.workflowStep2(passageText, group);
                    List<Map<String, Object>> groupQuestions = (List<Map<String, Object>>) step2Result.get("questions");
                    if (groupQuestions != null && !groupQuestions.isEmpty()) {
                        // Ensure options from Step1 are carried over if Step2 didn't include them
                        Object groupOptions = group.get("options");
                        if (groupOptions != null) {
                            for (Map<String, Object> q : groupQuestions) {
                                if (q.get("options") == null) {
                                    q.put("options", groupOptions);
                                }
                            }
                        }
                        allQuestions.addAll(groupQuestions);
                        log.info("Exam {} – Workflow Step2 group '{}': {} questions resolved",
                                examId, range, groupQuestions.size());
                    } else {
                        log.warn("Exam {} – Workflow Step2 group '{}': returned 0 questions", examId, range);
                        // Fallback: build questions from Step1 skeleton without answers
                        buildFallbackQuestions(group, allQuestions);
                    }
                } catch (Exception step2Ex) {
                    log.warn("Exam {} – Workflow Step2 group '{}' failed: {}", examId, range, step2Ex.getMessage());
                    buildFallbackQuestions(group, allQuestions);
                }
            }

            // Assemble final result
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("passages", passages != null ? passages : List.of());
            result.put("questions", allQuestions);
            log.info("Exam {} – Workflow complete: {} passages, {} questions",
                    examId, passages != null ? passages.size() : 0, allQuestions.size());
            return result;

        } catch (Exception workflowEx) {
            log.warn("Exam {} – Workflow failed ({}), falling back to parseSingle", examId, workflowEx.getMessage());
            return parseSingle(examId, text);
        }
    }

    /**
     * Build fallback questions from Step1 skeleton when Step2 fails for a group.
     * These will have text but no answer/explanation (user can still see the questions).
     */
    @SuppressWarnings("unchecked")
    private void buildFallbackQuestions(Map<String, Object> group, List<Map<String, Object>> target) {
        String type = String.valueOf(group.getOrDefault("type", "fill"));
        String range = String.valueOf(group.getOrDefault("range", ""));
        List<String> questionTexts = (List<String>) group.get("questions");
        Object options = group.get("options");

        // Parse range like "1-8" or "14"
        int startNum = 1;
        try {
            startNum = Integer.parseInt(range.contains("-") ? range.split("-")[0].trim() : range.trim());
        } catch (NumberFormatException ignored) {}

        if (questionTexts != null) {
            for (int i = 0; i < questionTexts.size(); i++) {
                Map<String, Object> q = new LinkedHashMap<>();
                q.put("questionNumber", startNum + i);
                q.put("type", type);
                q.put("text", questionTexts.get(i));
                q.put("answer", "");
                q.put("explanation", "解析失败，请手动作答");
                q.put("locatorText", "");
                if (options != null) q.put("options", options);
                target.add(q);
            }
        }
    }

    // ── Persist one section to DB ─────────────────────────────────────────────

    /**
     * Backward-compatible overload without rawText.
     */
    private void commitSection(Long examId, Map<String, Object> section,
                               Map<String, Object> parsed, Exam examHint) throws Exception {
        commitSection(examId, section, parsed, examHint, null);
    }

    /**
     * @param examId   target exam record id
     * @param section  if non-null, use section map directly (multi-section path)
     * @param parsed   if non-null, use this parsed map (single-section path)
     * @param examHint pre-loaded Exam entity to update in-place (may be null → load from DB)
     * @param rawText  original raw text input (for passage recovery if AI truncates)
     */
    @SuppressWarnings("unchecked")
    private void commitSection(Long examId, Map<String, Object> section,
                               Map<String, Object> parsed, Exam examHint, String rawText) throws Exception {
        Map<String, Object> data = section != null ? section : parsed;
        if (!(data instanceof LinkedHashMap)) {
            data = new LinkedHashMap<>(data);
        }
        Exam exam = examHint != null ? examHint : examMapper.selectById(examId);

        List<Map<String, Object>> questions = (List<Map<String, Object>>) data.get("questions");
        List<String> passages = (List<String>) data.get("passages");

        // ── Passage recovery: if AI truncated passage due to token limits, extract from raw text ──
        if (rawText != null && rawText.length() > 500) {
            int aiPassageLen = passages == null ? 0 : passages.stream().mapToInt(String::length).sum();
            if (aiPassageLen < rawText.length() / 4) {
                String recovered = extractPassageFromRawText(rawText);
                if (recovered != null && recovered.length() > aiPassageLen) {
                    log.info("Exam {} – AI passage too short ({} chars), recovered {} chars from raw text",
                            examId, aiPassageLen, recovered.length());
                    passages = new ArrayList<>(List.of(recovered));
                    data.put("passages", passages);
                }
            }
        }

        // Fix common reading parse issues:
        // 1) Question-group instructions (e.g. "Choose NO MORE THAN TWO WORDS...") mistakenly included in passages
        // 2) Passage and questions coupled in same block → split by detecting question numbering
        // 3) Ensure answers obey word-count constraints when instruction is present
        normalizeReadingInstructionsAndSplit(data);
        questions = (List<Map<String, Object>>) data.get("questions");
        passages = (List<String>) data.get("passages");

        // ── Fallback: AI returned 0 questions but passage contains question text ──
        // Re-parse the combined text with AI if we detect question markers in passages
        if ((questions == null || questions.isEmpty()) && passages != null && !passages.isEmpty()) {
            String combined = String.join("\n\n", passages);
            if (combined.toLowerCase().contains("questions") && 
                    Pattern.compile("\\b(?:TRUE|FALSE|NOT GIVEN|YES|NO|Choose the correct letter|answer sheet|on your answer sheet)\\b|^\\s*\\d{1,2}\\s+[A-Z][a-z]", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(combined).find()) {
                log.warn("Exam {} – AI returned 0 questions but passages contain question markers, retrying parse", examId);
                try {
                    Map<String, Object> retryResult = aiParseService.isConfigured()
                            ? aiParseService.parseWithAi(combined)
                            : fileParseService.parseExamContent(combined);
                    List<Map<String, Object>> retryQ = (List<Map<String, Object>>) retryResult.get("questions");
                    if (retryQ != null && !retryQ.isEmpty()) {
                        List<String> retryP = (List<String>) retryResult.get("passages");
                        if (retryP != null && !retryP.isEmpty()) {
                            data.put("passages", retryP);
                            passages = retryP;
                        }
                        data.put("questions", retryQ);
                        questions = retryQ;
                        log.info("Exam {} – Retry parse succeeded: {} questions recovered", examId, retryQ.size());
                    }
                } catch (Exception retryEx) {
                    log.warn("Exam {} – Retry parse also failed: {}", examId, retryEx.getMessage());
                }
            }
        }

        exam.setParseResult(objectMapper.writeValueAsString(data));
        exam.setStatus("ready");
        
        // Auto-generate writing task question if questions is empty but passage contains writing task
        if ((questions == null || questions.isEmpty()) && passages != null && !passages.isEmpty()) {
            String combinedPassage = String.join("\n\n", passages);
            String lowerPassage = combinedPassage.toLowerCase();
            if (lowerPassage.contains("writing task") || lowerPassage.contains("task 1") || lowerPassage.contains("task 2") ||
                lowerPassage.contains("summarise the information") || lowerPassage.contains("write an essay")) {
                log.info("Exam {} – Auto-generating writing task question from passage", examId);
                questions = new ArrayList<>();
                Map<String, Object> writeQuestion = new LinkedHashMap<>();
                writeQuestion.put("questionNumber", 1);
                writeQuestion.put("type", "write");
                
                // Extract task type from passage
                String taskType = "Task2";
                if (lowerPassage.contains("task 1")) {
                    taskType = "Task1";
                }
                writeQuestion.put("taskType", taskType);
                
                // Extract word limit from passage
                int wordLimit = 250;
                if (lowerPassage.contains("150 words")) {
                    wordLimit = 150;
                }
                writeQuestion.put("wordLimit", wordLimit);
                
                // Use the full passage as the question text
                writeQuestion.put("text", combinedPassage);
                writeQuestion.put("answer", "Approach: Identify main trends, compare data across regions, and highlight significant features.");
                writeQuestion.put("explanation", "Task Achievement: Report main features and comparisons. Coherence: Organize logically. Vocabulary: Use precise terms. Grammar: Use varied structures.");
                writeQuestion.put("locatorText", "summarise the information");
                
                questions.add(writeQuestion);
                data.put("questions", questions);
            }
        }
        
        if (questions != null) {
            // If AI returns duplicated or missing questionNumber (common when multiple small passages are parsed together),
            // normalize to a stable sequential numbering so UI/DB are consistent.
            ensureSequentialQuestionNumbers(questions);

            // Pre-scan: extract shared options list (A-P / A-H style) for list-selection questions.
            // AI often fails to attach these options to individual questions.
            Map<String, String> sharedOptionsList = extractSharedOptionsFromText(passages);
            if (sharedOptionsList.isEmpty()) {
                // Fallback: extract from question explanation fields (AI often references "Option E: '...'")
                sharedOptionsList = extractSharedOptionsFromQuestions(questions);
            }
            if (!sharedOptionsList.isEmpty()) {
                log.info("Exam {} – Extracted {} shared options: {}", examId, sharedOptionsList.size(), sharedOptionsList.keySet());
            }

            exam.setQuestionCount(questions.size());
            for (int i = 0; i < questions.size(); i++) {
                Map<String, Object> q = questions.get(i);
                Question question = new Question();
                question.setExamId(examId);
                Object numObj = q.get("questionNumber");
                question.setQuestionNumber(numObj instanceof Number ? ((Number) numObj).intValue() : i + 1);
                String type = (String) q.getOrDefault("type", "fill");
                String text = (String) q.getOrDefault("text", "");
                
                // Auto-detect writing task if AI didn't set type correctly
                if (!"write".equals(type) && !"fill".equals(type) && !"mcq".equals(type) && !"tfng".equals(type)) {
                    // Check if text contains writing task indicators
                    String lowerText = text.toLowerCase();
                    if (lowerText.contains("task 1") || lowerText.contains("task 2") ||
                        lowerText.contains("writing task") || lowerText.contains("summarise the information") ||
                        lowerText.contains("write an essay") || lowerText.contains("describe the")) {
                        type = "write";
                        log.info("Exam {} – Auto-detected writing task from text for question {}", examId, question.getQuestionNumber());
                    }
                }
                
                // Truncate answer to fit database column limit (VARCHAR(500))
                String answer = (String) q.getOrDefault("answer", "");

                // Auto-convert: if AI tagged as "fill" but answer is a single letter (A-Z),
                // it's likely a list-selection / MCQ question (e.g. choose from A-P)
                if ("fill".equals(type) && answer.trim().length() == 1 && Character.isLetter(answer.trim().charAt(0))) {
                    type = "mcq";
                    // Inject shared options if AI didn't provide them
                    if (q.get("options") == null && !sharedOptionsList.isEmpty()) {
                        q.put("options", new LinkedHashMap<>(sharedOptionsList));
                        log.info("Exam {} – Question {} auto-converted fill→mcq with {} shared options (answer '{}')",
                                examId, question.getQuestionNumber(), sharedOptionsList.size(), answer.trim());
                    } else if (q.get("options") == null) {
                        log.info("Exam {} – Question {} auto-converted fill→mcq but no shared options found (answer '{}')",
                                examId, question.getQuestionNumber(), answer.trim());
                    }
                }

                question.setType(type);
                question.setQuestionText(text);

                if (answer.length() > 500) {
                    answer = answer.substring(0, 500);
                    log.warn("Exam {} – Question {} answer truncated from {} to 500 chars", examId, question.getQuestionNumber(), ((String) q.get("answer")).length());
                }

                // For fill questions, if the model produced a statement that already includes the answer verbatim
                // but did not include any blank marker, convert it into a proper fill-in-the-blank.
                if ("fill".equals(type)) {
                    text = ensureFillHasBlank(text, answer);
                    question.setQuestionText(text);
                }

                question.setAnswer(answer);
                question.setExplanation((String) q.getOrDefault("explanation", ""));
                question.setLocatorText((String) q.getOrDefault("locatorText", ""));
                log.info("Exam {} – Question {} type: '{}', text: '{}'", examId, question.getQuestionNumber(), type, text.substring(0, Math.min(100, text.length())));
                if ("write".equals(type)) {
                    Map<String, Object> writeExtra = new java.util.LinkedHashMap<>();
                    writeExtra.put("taskType", q.getOrDefault("taskType", "Task2"));
                    Object wl = q.get("wordLimit");
                    writeExtra.put("wordLimit", wl instanceof Number ? ((Number) wl).intValue() : 250);
                    question.setOptions(objectMapper.writeValueAsString(writeExtra));
                } else {
                    Object opts = q.get("options");
                    // Fix: if options is a plain string "A: text\nB: text\n...", parse to map
                    if (opts instanceof String) {
                        String optStr = (String) opts;
                        Map<String, String> parsedOpts = parseOptionsTextToMap(optStr);
                        if (!parsedOpts.isEmpty()) {
                            opts = parsedOpts;
                            log.info("Exam {} – Question {} parsed string options → {} entries",
                                    examId, question.getQuestionNumber(), parsedOpts.size());
                        }
                    }
                    // Fix: if options is a plain array of single-letter strings (e.g. ["A","B",...,"P"]),
                    // replace with shared options map that has actual text content
                    if (opts instanceof List) {
                        List<?> optList = (List<?>) opts;
                        boolean allSingleLetters = !optList.isEmpty() && optList.stream().allMatch(
                                o -> o instanceof String && ((String) o).length() == 1 && Character.isLetter(((String) o).charAt(0)));
                        if (allSingleLetters && !sharedOptionsList.isEmpty()) {
                            // Convert letter array to map using shared options text
                            Map<String, String> fixedOpts = new LinkedHashMap<>();
                            for (Object o : optList) {
                                String letter = ((String) o).toUpperCase();
                                fixedOpts.put(letter, sharedOptionsList.getOrDefault(letter, letter));
                            }
                            opts = fixedOpts;
                            log.info("Exam {} – Question {} fixed letter-array options → {} entries with text",
                                    examId, question.getQuestionNumber(), fixedOpts.size());
                        } else if (allSingleLetters) {
                            // No shared options found; convert to map with letter as text
                            Map<String, String> fallbackOpts = new LinkedHashMap<>();
                            for (Object o : optList) {
                                String letter = ((String) o).toUpperCase();
                                fallbackOpts.put(letter, letter);
                            }
                            opts = fallbackOpts;
                        }
                    }
                    // Also inject shared options for mcq questions that have NO options at all
                    if ("mcq".equals(type) && opts == null && !sharedOptionsList.isEmpty()) {
                        opts = new LinkedHashMap<>(sharedOptionsList);
                        log.info("Exam {} – Question {} injected {} shared options for mcq",
                                examId, question.getQuestionNumber(), sharedOptionsList.size());
                    }
                    if (opts != null) question.setOptions(objectMapper.writeValueAsString(opts));
                }
                questionMapper.insert(question);
            }
        }

        // Auto-detect exam type from questions
        if (section != null) {
            String sectionType = (String) section.getOrDefault("type", "");
            if ("writing".equals(sectionType)) exam.setType("writing");
            else if ("listening".equals(sectionType)) exam.setType("listening");
            else if ("reading".equals(sectionType)) exam.setType("reading");
        } else if (questions != null && !questions.isEmpty()) {
            boolean anyWrite = questions.stream().anyMatch(q -> "write".equals(q.get("type")));
            if (anyWrite) exam.setType("writing");
        }

        // ── Auto-generate difficulty and tags ────────────────────────────────
        exam.setDifficulty(assessDifficulty(exam, questions, passages));
        exam.setTags(objectMapper.writeValueAsString(generateTags(exam, questions, passages)));

        examMapper.updateById(exam);
        log.info("Exam {} committed – {} questions, difficulty={}, tags={}", examId,
                questions != null ? questions.size() : 0, exam.getDifficulty(), exam.getTags());
    }

    @SuppressWarnings("unchecked")
    private void normalizeReadingInstructionsAndSplit(Map<String, Object> data) {
        Object passagesObj = data.get("passages");
        Object questionsObj = data.get("questions");
        if (!(passagesObj instanceof List)) return;
        List<String> passages = new ArrayList<>();
        for (Object p : (List<?>) passagesObj) {
            if (p != null) passages.add(p.toString());
        }

        List<Map<String, Object>> questions = null;
        if (questionsObj instanceof List) {
            questions = new ArrayList<>();
            for (Object q : (List<?>) questionsObj) {
                if (q instanceof Map) questions.add(new LinkedHashMap<>((Map<String, Object>) q));
            }
        }

        // 1) If passage contains questions/instructions, split at the first question marker.
        List<String> cleanedPassages = new ArrayList<>();
        StringBuilder extractedInstruction = new StringBuilder();
        for (String passage : passages) {
            if (passage == null || passage.isBlank()) continue;

            // Split if questions are coupled into the passage text
            Matcher qm = QUESTION_START_MARKER.matcher(passage);
            String onlyPassage = passage;
            if (qm.find()) {
                int cut = qm.start();
                // Guard: marker near the beginning is usually a question-block header rather than a real split point.
                // If we split too early, we may wipe the actual passage and lose multi-passage separation.
                if (cut > 200 && cut < passage.length()) {
                    onlyPassage = passage.substring(0, cut).trim();
                    // the rest is likely question area (contains instructions); extract instruction-like lines
                    String remainder = passage.substring(cut).trim();
                    extractInstructionLines(remainder, extractedInstruction);
                }
            }

            // Remove instruction lines that sometimes get stuck inside passage
            String withoutInstructions = removeInstructionLines(onlyPassage, extractedInstruction);
            if (!withoutInstructions.isBlank()) cleanedPassages.add(withoutInstructions);
        }

        // 2) If we have extracted instruction, prepend it to fill questions' text
        String instructionBlock = extractedInstruction.toString().trim();
        if (!instructionBlock.isBlank() && questions != null && !questions.isEmpty()) {
            for (Map<String, Object> q : questions) {
                String type = Objects.toString(q.getOrDefault("type", ""), "");
                if (!"fill".equalsIgnoreCase(type)) continue;
                String text = Objects.toString(q.getOrDefault("text", ""), "").trim();
                if (text.isBlank()) continue;
                // Avoid duplicating if already contains the instruction
                if (!normalize(text).contains(normalize(instructionBlock))) {
                    q.put("text", instructionBlock + "\n\n" + text);
                }
            }
        }

        // 3) Persist normalized data
        data.put("passages", cleanedPassages);
        if (questions != null) data.put("questions", questions);
    }

    private static void ensureSequentialQuestionNumbers(List<Map<String, Object>> questions) {
        if (questions == null || questions.isEmpty()) return;
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        boolean hasDupOrMissing = false;
        for (Map<String, Object> q : questions) {
            Object n = q.get("questionNumber");
            if (!(n instanceof Number)) { hasDupOrMissing = true; continue; }
            int v = ((Number) n).intValue();
            if (v <= 0 || !seen.add(v)) { hasDupOrMissing = true; }
        }
        if (!hasDupOrMissing) return;
        for (int i = 0; i < questions.size(); i++) {
            questions.get(i).put("questionNumber", i + 1);
        }
    }

    private static String ensureFillHasBlank(String questionText, String answer) {
        if (questionText == null) return "";
        String t = questionText.trim();
        if (t.isBlank()) return t;

        // already has blank markers
        if (t.contains("________") || t.matches(".*_{3,}.*")) return t;

        String a = answer == null ? "" : answer.trim();
        if (a.isBlank()) return t;

        // Skip if answer is very short (1-2 chars) — replacing single letters in text corrupts words
        if (a.length() <= 2) return t;

        // Replace first exact occurrence (case-insensitive) of answer with blank
        Pattern p = Pattern.compile(Pattern.quote(a), Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(t);
        if (m.find()) {
            return m.replaceFirst("________");
        }
        return t;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private static void extractInstructionLines(String text, StringBuilder out) {
        if (text == null || text.isBlank()) return;
        for (String line : text.split("\\r?\\n")) {
            String ln = line.trim();
            if (ln.isBlank()) continue;
            if (INSTRUCTION_LINE.matcher(ln).matches()) {
                if (out.length() > 0) out.append("\n");
                out.append(ln);
            }
        }
    }

    private static String removeInstructionLines(String passage, StringBuilder extractedInstruction) {
        if (passage == null || passage.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        for (String line : passage.split("\\r?\\n")) {
            String ln = line.trim();
            if (ln.isBlank()) {
                sb.append("\n");
                continue;
            }
            if (INSTRUCTION_LINE.matcher(ln).matches()) {
                if (extractedInstruction.length() > 0) extractedInstruction.append("\n");
                extractedInstruction.append(ln);
                continue;
            }
            sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }

    private static String enforceWordLimitFromQuestionText(String questionText, String answer) {
        if (answer == null) return "";
        String a = answer.trim();
        if (a.isBlank()) return a;
        String qt = questionText == null ? "" : questionText;

        int limit = extractWordLimit(qt);
        if (limit <= 0) return a;

        // Keep only first N whitespace-separated tokens
        String[] tokens = a.split("\\s+");
        if (tokens.length <= limit) return a;
        return String.join(" ", java.util.Arrays.copyOf(tokens, limit));
    }

    private static int extractWordLimit(String questionText) {
        if (questionText == null) return -1;
        Matcher m = ANSWER_WORD_LIMIT.matcher(questionText);
        if (!m.find()) return -1;
        String wordNum = m.group(1);
        String digit = m.group(2);
        if (digit != null) {
            try { return Integer.parseInt(digit); } catch (NumberFormatException ignored) { return -1; }
        }
        if (wordNum != null) {
            return WORD_NUM.getOrDefault(wordNum.toUpperCase(), -1);
        }
        return -1;
    }

    // ── Shared options extraction ──────────────────────────────────────────────

    /**
     * Scans passages for a shared options list like:
     *   A  There is a complicated combination of reasons...
     *   B  The rainforests are being destroyed...
     *   ...
     *   P  Humans depend on the rainforests...
     * Returns a map of {"A": "...", "B": "...", ...} or empty if not found.
     */
    // Matches lines like: "A  There is a complicated..." or "A. There is..." or "A) There is..."
    private static final Pattern OPTION_LINE = Pattern.compile(
            "^\\s*([A-Z])[.)\\s]\\s*([A-Z].{5,})$", Pattern.MULTILINE);

    // Matches references in explanations like: "Option E: 'Without rainforests...'", "response E", "statement E" etc.
    private static final Pattern EXPLANATION_OPTION_REF = Pattern.compile(
            "(?:Option|response|answer|statement)\\s+([A-Z])(?:\\s+is|[:\\s,])\\s*['\"]([^'\"]{5,}?)['\"]", Pattern.CASE_INSENSITIVE);

    private Map<String, String> extractSharedOptionsFromText(List<String> texts) {
        if (texts == null || texts.isEmpty()) return Map.of();
        Map<String, String> opts = new LinkedHashMap<>();
        for (String text : texts) {
            if (text == null) continue;
            Matcher m = OPTION_LINE.matcher(text);
            while (m.find()) {
                opts.put(m.group(1), m.group(2).trim());
            }
        }
        // Only consider valid if we found at least 4 options starting from A
        if (opts.size() >= 4 && opts.containsKey("A")) {
            return opts;
        }
        return Map.of();
    }

    /**
     * Extract shared options from question explanations.
     * AI often writes "Option E: 'Without rainforests...'" in the explanation.
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> extractSharedOptionsFromQuestions(List<Map<String, Object>> questions) {
        if (questions == null) return Map.of();
        Map<String, String> opts = new LinkedHashMap<>();
        for (Map<String, Object> q : questions) {
            // Scan explanation for option references
            String explanation = String.valueOf(q.getOrDefault("explanation", ""));
            Matcher m = EXPLANATION_OPTION_REF.matcher(explanation);
            while (m.find()) {
                opts.put(m.group(1), m.group(2).trim());
            }
            // Also scan question text for option lines (A-P style lines sometimes end up in text)
            String text = String.valueOf(q.getOrDefault("text", ""));
            Matcher tm = OPTION_LINE.matcher(text);
            while (tm.find()) {
                opts.putIfAbsent(tm.group(1), tm.group(2).trim());
            }
        }
        if (opts.size() >= 3) return opts;
        return Map.of();
    }

    private void markError(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam != null) { exam.setStatus("error"); examMapper.updateById(exam); }
    }

    // ── Difficulty assessment ──────────────────────────────────────────────────

    /**
     * Assess exam difficulty on a 1-5 scale based on question types, count, and passage complexity.
     * Returns a Chinese label: 入门(1), 简单(2), 中等(3), 较难(4), 困难(5)
     */
    @SuppressWarnings("unchecked")
    private String assessDifficulty(Exam exam, List<Map<String, Object>> questions, List<String> passages) {
        if (questions == null || questions.isEmpty()) return "中等";

        int score = 0;
        int count = questions.size();

        // Factor 1: question count
        if (count <= 5) score += 1;
        else if (count <= 13) score += 2;
        else if (count <= 27) score += 3;
        else if (count <= 40) score += 4;
        else score += 5;

        // Factor 2: question type complexity
        long tfngCount = questions.stream().filter(q -> "tfng".equals(q.get("type"))).count();
        long mcqCount = questions.stream().filter(q -> "mcq".equals(q.get("type"))).count();
        long fillCount = questions.stream().filter(q -> "fill".equals(q.get("type"))).count();
        long writeCount = questions.stream().filter(q -> "write".equals(q.get("type"))).count();

        // Writing tasks are inherently harder
        if (writeCount > 0) {
            boolean hasTask2 = questions.stream().anyMatch(q ->
                    "Task2".equals(q.get("taskType")) || "task2".equalsIgnoreCase(String.valueOf(q.get("taskType"))));
            score += hasTask2 ? 4 : 3;
        } else {
            // Fill-in-blank > TFNG > MCQ in difficulty
            double typeDiff = (fillCount * 3.0 + tfngCount * 2.5 + mcqCount * 1.5) / Math.max(count, 1);
            score += (int) Math.round(typeDiff);
        }

        // Factor 3: passage length (longer = harder)
        if (passages != null && !passages.isEmpty()) {
            int totalLen = passages.stream().mapToInt(String::length).sum();
            if (totalLen > 5000) score += 2;
            else if (totalLen > 2000) score += 1;
        }

        // Normalize to 1-5 scale (score range: roughly 2-12)
        int level;
        if (score <= 3) level = 1;
        else if (score <= 5) level = 2;
        else if (score <= 7) level = 3;
        else if (score <= 9) level = 4;
        else level = 5;

        return switch (level) {
            case 1 -> "入门";
            case 2 -> "简单";
            case 3 -> "中等";
            case 4 -> "较难";
            case 5 -> "困难";
            default -> "中等";
        };
    }

    // ── Tag generation ────────────────────────────────────────────────────────

    /**
     * Generate tags based on exam type, title, and question content.
     * Includes: type label, Academic/General, Task 1/Task 2, Cambridge source, etc.
     */
    @SuppressWarnings("unchecked")
    private List<String> generateTags(Exam exam, List<Map<String, Object>> questions, List<String> passages) {
        List<String> tags = new ArrayList<>();
        String type = exam.getType() != null ? exam.getType().toLowerCase() : "reading";
        String title = exam.getTitle() != null ? exam.getTitle().toLowerCase() : "";
        String combinedPassage = passages != null ? String.join(" ", passages).toLowerCase() : "";

        // Tag 1: Exam type
        switch (type) {
            case "writing" -> tags.add("Writing");
            case "listening" -> tags.add("Listening");
            default -> tags.add("Reading");
        }

        // Tag 2: Academic vs General Training
        if (title.contains("general") || combinedPassage.contains("general training")) {
            tags.add("General");
        } else {
            tags.add("Academic");
        }

        // Tag 3: Writing Task 1 / Task 2
        if ("writing".equals(type) && questions != null) {
            boolean hasTask1 = false, hasTask2 = false;
            for (Map<String, Object> q : questions) {
                String taskType = String.valueOf(q.getOrDefault("taskType", ""));
                if ("Task1".equalsIgnoreCase(taskType)) hasTask1 = true;
                if ("Task2".equalsIgnoreCase(taskType)) hasTask2 = true;
            }
            // Also check passage content and title
            if (!hasTask1 && (title.contains("task 1") || combinedPassage.contains("task 1"))) hasTask1 = true;
            if (!hasTask2 && (title.contains("task 2") || combinedPassage.contains("task 2"))) hasTask2 = true;
            // Default to Task 2 if neither detected
            if (!hasTask1 && !hasTask2) hasTask2 = true;

            if (hasTask1) tags.add("Task 1");
            if (hasTask2) tags.add("Task 2");
        }

        // Tag 4: Cambridge source detection
        if (title.matches(".*cambridge.*ielts.*\\d+.*") || title.matches(".*剑\\d+.*") || title.matches(".*剑桥.*\\d+.*")) {
            tags.add("真题");
        }

        return tags;
    }

    /**
     * Extract the reading passage from raw text by stripping question sections.
     * Looks for where questions start (e.g. "Questions 1-8", numbered items like "1  The plight...")
     * and returns everything before that as the passage.
     */
    private String extractPassageFromRawText(String rawText) {
        if (rawText == null || rawText.isBlank()) return null;

        // Try to find where questions start
        Pattern questionsStart = Pattern.compile(
                "(?im)^\\s*(?:Questions?\\s+\\d+|\\d{1,2}\\s+(?:TRUE|FALSE|NOT GIVEN|YES|NO|The |What |Which |How |Why |Where |Who ))");
        Matcher m = questionsStart.matcher(rawText);
        if (m.find() && m.start() > 200) {
            String passage = rawText.substring(0, m.start()).trim();
            // Also strip section headers like "Reading Passage 1"
            passage = passage.replaceAll("(?i)^\\s*(Reading Passage \\d+|Section \\d+|Part \\d+)\\s*\\n?", "").trim();
            if (passage.length() > 200) {
                return passage;
            }
        }

        // Fallback: look for option list start (A  text\nB  text) or numbered question patterns
        Pattern optionListStart = Pattern.compile("^[A-P]\\s{2,}\\S", Pattern.MULTILINE);
        Matcher m2 = optionListStart.matcher(rawText);
        if (m2.find() && m2.start() > 200) {
            return rawText.substring(0, m2.start()).trim();
        }

        return null;
    }

    /**
     * Parse a multi-line string of options in "A: text\nB: text\n..." or "A  text\nB  text\n..." format
     * into a map of letter -> description text.
     */
    private Map<String, String> parseOptionsTextToMap(String text) {
        Map<String, String> options = new LinkedHashMap<>();
        if (text == null || text.isBlank()) return options;

        String[] lines = text.split("\\n");
        String currentLetter = null;
        StringBuilder currentText = new StringBuilder();

        Pattern pattern = Pattern.compile("^([A-P])[:：]\\s*(.+)$");
        Pattern pattern2 = Pattern.compile("^([A-P])\\s{2,}(.+)$");

        for (String line : lines) {
            String trimmed = line.trim();
            Matcher m = pattern.matcher(trimmed);
            if (!m.matches()) m = pattern2.matcher(trimmed);
            if (m.matches()) {
                if (currentLetter != null) {
                    options.put(currentLetter, currentText.toString().trim());
                }
                currentLetter = m.group(1);
                currentText = new StringBuilder(m.group(2));
            } else if (currentLetter != null && !trimmed.isEmpty()
                    && !trimmed.matches("^\\d+\\s+.*")
                    && !trimmed.matches("^(?i)questions?\\s+.*")) {
                currentText.append(" ").append(trimmed);
            } else if (currentLetter != null) {
                options.put(currentLetter, currentText.toString().trim());
                currentLetter = null;
            }
        }
        if (currentLetter != null) {
            options.put(currentLetter, currentText.toString().trim());
        }
        return options.size() >= 2 ? options : new LinkedHashMap<>();
    }
}
