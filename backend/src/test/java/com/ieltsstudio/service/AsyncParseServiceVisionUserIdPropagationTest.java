package com.ieltsstudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieltsstudio.entity.Exam;
import com.ieltsstudio.mapper.ExamMapper;
import com.ieltsstudio.mapper.QuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 5C-2：验证 userId 从 {@code AsyncParseService} 的精准解析路径一路下传到
 * {@link QwenAiParseService#parseDocument(Long, byte[], String, String)} /
 * {@link QwenAiParseService#parseImages(Long, List, List, String)}。
 *
 * <p>不真实访问 Qwen / MiMO / DeepSeek，也不真实访问数据库：
 * {@link ExamMapper} / {@link QuestionMapper} / {@link FileParseService} /
 * {@link AiParseService} / {@link QwenAiParseService} 均用 Mockito mock；
 * {@link ObjectMapper} 用真实实例。</p>
 */
class AsyncParseServiceVisionUserIdPropagationTest {

    private static final Long USER_ID = 88L;
    private static final Long EXAM_ID = 2002L;

    private ExamMapper examMapper;
    private QuestionMapper questionMapper;
    private FileParseService fileParseService;
    private AiParseService aiParseService;
    private QwenAiParseService qwenAiParseService;
    private AsyncParseService service;

    @BeforeEach
    void setUp() {
        examMapper = mock(ExamMapper.class);
        questionMapper = mock(QuestionMapper.class);
        fileParseService = mock(FileParseService.class);
        aiParseService = mock(AiParseService.class);
        qwenAiParseService = mock(QwenAiParseService.class);
        service = new AsyncParseService(examMapper, questionMapper, fileParseService,
                aiParseService, qwenAiParseService, new ObjectMapper());
    }

    /** 构造一个简单的精准解析返回（单 section、1 道 fill 题，避免触发 commitSection 内部 retry）。 */
    private Map<String, Object> fakeParsed() {
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("passages", List.of("sample passage for vision precise parse"));
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("questionNumber", 1);
        q.put("type", "fill");
        q.put("text", "Sample fill question text");
        q.put("answer", "answer");
        q.put("explanation", "explanation");
        q.put("locatorText", "sample passage");
        parsed.put("questions", List.of(q));
        return parsed;
    }

    private Exam fakeExam() {
        Exam exam = new Exam();
        exam.setId(EXAM_ID);
        exam.setUserId(USER_ID);
        exam.setType("reading");
        exam.setStatus("processing");
        return exam;
    }

    // ── 1. parseAndSave(parsePrecise=true) 把 userId 透传给 parseDocument ──────

    @Test
    void parseAndSaveShouldPassUserIdToVisionParseDocumentWhenPrecise() throws Exception {
        when(qwenAiParseService.isConfigured(USER_ID)).thenReturn(true);
        when(qwenAiParseService.parseDocument(eq(USER_ID), any(byte[].class), eq("test.pdf"), eq("reading")))
                .thenReturn(fakeParsed());
        when(examMapper.selectById(EXAM_ID)).thenReturn(fakeExam());

        byte[] fileBytes = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D}; // "%PDF-" magic
        service.parseAndSave(USER_ID, EXAM_ID, fileBytes, "test.pdf", true, null, "reading");

        // 校验 userId 被透传到 parseDocument
        verify(qwenAiParseService).parseDocument(eq(USER_ID), any(byte[].class), eq("test.pdf"), eq("reading"));
        // 后续 handleQwenParsedResult 会加载 Exam
        verify(examMapper).selectById(EXAM_ID);
    }

    // ── 2. parseAndSaveImages(parsePrecise=true) 把 userId 透传给 parseImages ─

    @Test
    void parseAndSaveImagesShouldPassUserIdToVisionParseImagesWhenPrecise() throws Exception {
        when(qwenAiParseService.isConfigured(USER_ID)).thenReturn(true);
        when(qwenAiParseService.parseImages(eq(USER_ID), any(), any(), eq("reading")))
                .thenReturn(fakeParsed());
        when(examMapper.selectById(EXAM_ID)).thenReturn(fakeExam());

        byte[] imgBytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        service.parseAndSaveImages(USER_ID, EXAM_ID, List.of(imgBytes), List.of("a.png"), true, null, "reading");

        // 校验 userId 被透传到 parseImages
        verify(qwenAiParseService).parseImages(eq(USER_ID), any(), any(), eq("reading"));
        verify(examMapper).selectById(EXAM_ID);
    }
}
