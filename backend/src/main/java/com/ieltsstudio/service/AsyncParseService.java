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
                
                Map<String, Object> parsed = parseSingle(examId, text);
                commitSection(examId, null, parsed, null);
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
            Map<String, Object> parsed = parseSingle(examId, extractedText);
            commitSection(examId, null, parsed, null);
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
            // All chunks failed – fall back to single parse
            log.warn("Exam {} – all chunks failed, falling back to single parse", examId);
            Map<String, Object> parsed = parseSingle(examId, text);
            commitSection(examId, null, parsed, original);
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

        commitSection(examId, null, merged, original);
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

    // ── Persist one section to DB ─────────────────────────────────────────────

    /**
     * @param examId   target exam record id
     * @param section  if non-null, use section map directly (multi-section path)
     * @param parsed   if non-null, use this parsed map (single-section path)
     * @param examHint pre-loaded Exam entity to update in-place (may be null → load from DB)
     */
    @SuppressWarnings("unchecked")
    private void commitSection(Long examId, Map<String, Object> section,
                               Map<String, Object> parsed, Exam examHint) throws Exception {
        Map<String, Object> data = section != null ? section : parsed;
        Exam exam = examHint != null ? examHint : examMapper.selectById(examId);

        exam.setParseResult(objectMapper.writeValueAsString(data));
        exam.setStatus("ready");

        List<Map<String, Object>> questions = (List<Map<String, Object>>) data.get("questions");
        List<String> passages = (List<String>) data.get("passages");
        
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
                
                question.setType(type);
                question.setQuestionText(text);
                
                // Truncate answer to fit database column limit (VARCHAR(500))
                String answer = (String) q.getOrDefault("answer", "");
                if (answer.length() > 500) {
                    answer = answer.substring(0, 500);
                    log.warn("Exam {} – Question {} answer truncated from {} to 500 chars", examId, question.getQuestionNumber(), ((String) q.get("answer")).length());
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

        examMapper.updateById(exam);
        log.info("Exam {} committed – {} questions", examId, questions != null ? questions.size() : 0);
    }

    private void markError(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam != null) { exam.setStatus("error"); examMapper.updateById(exam); }
    }
}
