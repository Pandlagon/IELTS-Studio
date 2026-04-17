package com.ieltsstudio.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;
import java.util.regex.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileParseService {

    public String extractText(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        if (filename == null) throw new RuntimeException("文件名不能为空");
        return extractTextFromBytes(file.getBytes(), filename);
    }

    public String extractTextFromBytes(byte[] bytes, String filename) throws Exception {
        if (filename == null) throw new RuntimeException("文件名不能为空");

        if (filename.toLowerCase().endsWith(".pdf")) {
            return extractFromPdf(new java.io.ByteArrayInputStream(bytes));
        } else if (filename.toLowerCase().endsWith(".docx")) {
            return extractFromDocx(new java.io.ByteArrayInputStream(bytes));
        } else if (filename.toLowerCase().endsWith(".doc")) {
            return extractFromDoc(new java.io.ByteArrayInputStream(bytes));
        } else {
            throw new RuntimeException("不支持的文件格式，请上传 PDF 或 Word 文件");
        }
    }

    private String extractFromPdf(InputStream is) throws Exception {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(is))) {
            int pages = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            log.info("PDFBox extracted {} chars from {} page(s){}",
                    text.trim().length(), pages,
                    text.trim().isEmpty() ? " – likely scanned/image-based PDF" : "");
            return text;
        }
    }

    private String extractFromDocx(InputStream is) throws Exception {
        try (XWPFDocument document = new XWPFDocument(is)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph para : document.getParagraphs()) {
                sb.append(para.getText()).append("\n");
            }
            return sb.toString();
        }
    }

    private String extractFromDoc(InputStream is) throws Exception {
        try (HWPFDocument document = new HWPFDocument(is)) {
            org.apache.poi.hwpf.usermodel.Range range = document.getRange();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < range.numParagraphs(); i++) {
                String text = range.getParagraph(i).text();
                // POI appends \r to each paragraph; normalize to \n
                sb.append(text.replace('\r', '\n'));
            }
            return sb.toString();
        }
    }

    // ── Main entry point ────────────────────────────────────────────
    public Map<String, Object> parseExamContent(String text) {
        Map<String, Object> result = new HashMap<>();

        // 1. Extract answer key if present (e.g. "Answers" / "Answer Key" section at end)
        Map<Integer, String> answerKey = extractAnswerKey(text);

        // 2. Split text into logical lines (collapse soft line-breaks inside paragraphs)
        List<String> lines = splitLines(text);

        // 3. Separate passages from question blocks
        List<String> passages = new ArrayList<>();
        List<Map<String, Object>> rawQBlocks = new ArrayList<>();
        segmentContent(lines, passages, rawQBlocks);

        // 4. Parse each question block into a structured question
        List<Map<String, Object>> questions = new ArrayList<>();
        for (Map<String, Object> block : rawQBlocks) {
            Map<String, Object> q = buildQuestion(block, answerKey);
            if (q != null) questions.add(q);
        }

        log.debug("Parsed {} passages, {} questions, {} answers from answer key",
                passages.size(), questions.size(), answerKey.size());

        result.put("passages", passages);
        result.put("questions", questions);
        result.put("questionCount", questions.size());
        return result;
    }

    // ── Answer key extraction ────────────────────────────────────────
    private Map<Integer, String> extractAnswerKey(String text) {
        Map<Integer, String> answers = new LinkedHashMap<>();
        // Look for an "Answers" or "Answer Key" section
        Pattern sectionPat = Pattern.compile(
            "(?i)\\b(answers?|answer\\s+key|解析|参考答案)\\b[\\s\\S]{0,200}?\\n(.{0,2000})",
            Pattern.DOTALL);
        Matcher sm = sectionPat.matcher(text);
        String answerSection = "";
        if (sm.find()) {
            answerSection = sm.group(2);
        }
        // Pattern: "1. TRUE" / "1  T" / "1. C" / "1 answer text"
        Pattern aPat = Pattern.compile("(?m)^\\s*(\\d{1,2})[\\.:\\s]+([A-Za-z][A-Za-z\\s]{0,40})$");
        Matcher am = aPat.matcher(answerSection.isEmpty() ? text : answerSection);
        while (am.find()) {
            int num = Integer.parseInt(am.group(1));
            String ans = am.group(2).trim().toUpperCase();
            // Normalise TFNG answers
            if (ans.equals("T") || ans.startsWith("TRUE")) ans = "TRUE";
            else if (ans.equals("F") || ans.startsWith("FALSE")) ans = "FALSE";
            else if (ans.startsWith("NOT")) ans = "NOT GIVEN";
            else if (ans.equals("Y") || ans.startsWith("YES")) ans = "YES";
            else if (ans.equals("N") || ans.startsWith("NO") && !ans.startsWith("NOT")) ans = "NO";
            answers.put(num, ans);
        }
        return answers;
    }

    // ── Line splitting (preserve paragraph boundaries) ────────────────
    private List<String> splitLines(String text) {
        List<String> out = new ArrayList<>();
        for (String raw : text.split("\n")) {
            String t = raw.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    // ── Segment content into passages + raw question blocks ──────────
    private static final Pattern Q_START = Pattern.compile(
        "^(\\d{1,2})[.、)）]\\s+(.+)" +           // "1. text" / "1、text"
        "|^(\\d{1,2})\\s{2,}(.+)" +                // "1  text" (double-space)
        "|^(?:Question|Q)(\\d{1,2})[.:\\s](.+)");  // "Question 1" / "Q1."

    private void segmentContent(List<String> lines,
                                List<String> passages,
                                List<Map<String, Object>> qBlocks) {
        StringBuilder passageBuf = new StringBuilder();
        Map<String, Object> currentQ = null;
        StringBuilder qBuf = new StringBuilder();
        boolean seenFirstQ = false;

        for (String line : lines) {
            Matcher m = Q_START.matcher(line);
            if (m.matches()) {
                seenFirstQ = true;
                // Flush passage
                if (passageBuf.length() > 50) {
                    passages.add(passageBuf.toString().trim());
                    passageBuf = new StringBuilder();
                }
                // Flush previous question
                if (currentQ != null) {
                    currentQ.put("body", qBuf.toString().trim());
                    qBlocks.add(currentQ);
                }
                currentQ = new HashMap<>();
                qBuf = new StringBuilder();
                // Extract question number
                String numStr = m.group(1) != null ? m.group(1)
                        : (m.group(3) != null ? m.group(3) : m.group(5));
                String firstText = m.group(2) != null ? m.group(2)
                        : (m.group(4) != null ? m.group(4) : m.group(6));
                currentQ.put("num", Integer.parseInt(numStr));
                qBuf.append(firstText != null ? firstText : "").append(" ");
            } else if (seenFirstQ && currentQ != null) {
                // Continuation of current question (options, sub-text)
                qBuf.append(line).append(" ");
            } else {
                // Still in passage
                passageBuf.append(line).append(" ");
            }
        }
        // Flush last question
        if (currentQ != null) {
            currentQ.put("body", qBuf.toString().trim());
            qBlocks.add(currentQ);
        }
        // Flush remaining passage text
        if (passageBuf.length() > 50) {
            passages.add(passageBuf.toString().trim());
        }
    }

    // ── Build a structured question map ──────────────────────────────
    private Map<String, Object> buildQuestion(Map<String, Object> block,
                                              Map<Integer, String> answerKey) {
        int num = (int) block.getOrDefault("num", 0);
        String body = (String) block.getOrDefault("body", "");
        if (body.isBlank()) return null;

        Map<String, Object> q = new HashMap<>();
        q.put("questionNumber", num);

        // Determine type
        String bodyLower = body.toLowerCase();
        String type;
        if (bodyLower.contains("true") || bodyLower.contains("false")
                || bodyLower.contains("not given")) {
            type = "tfng";
        } else if (bodyLower.contains("yes") || bodyLower.contains("no")
                || (bodyLower.contains("not") && bodyLower.contains("given"))) {
            type = "tfng";
        } else if (body.matches("(?s).*\\b[A-D][.)].{3,}.*")) {
            type = "mcq";
        } else if (body.contains("_____") || body.contains("......")
                || body.contains("…") || bodyLower.contains("complete")
                || bodyLower.contains("fill in")) {
            type = "fill";
        } else {
            type = "fill";
        }
        q.put("type", type);

        // Clean question text (strip leading number, options for MCQ)
        String text = body.replaceAll("(?s)\\b[A-D][.)].{1,100}", "").trim();
        text = text.replaceAll("\\s{2,}", " ");
        q.put("text", text);

        // Answer from key (if available)
        String answer = answerKey.getOrDefault(num, "");
        // For MCQ, look for option letters in the body
        if (answer.isEmpty() && "mcq".equals(type)) {
            Matcher optMatcher = Pattern.compile("Answer:\\s*([A-D])", Pattern.CASE_INSENSITIVE)
                    .matcher(body);
            if (optMatcher.find()) answer = optMatcher.group(1).toUpperCase();
        }
        q.put("answer", answer);
        q.put("explanation", answer.isEmpty() ? "请在答题后对照官方答案" : "答案来源：文档答案键");
        return q;
    }
}
