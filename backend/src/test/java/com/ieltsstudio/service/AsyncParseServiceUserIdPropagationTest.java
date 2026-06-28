package com.ieltsstudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieltsstudio.entity.Exam;
import com.ieltsstudio.mapper.ExamMapper;
import com.ieltsstudio.mapper.QuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 5C-1：验证 userId 从 {@code AsyncParseService.parseAndSave} 一路下传到
 * 普通文本解析 AI 调用 {@code AiParseService.parseWithAi(userId, ...)}。
 *
 * <p>不真实访问 DeepSeek / Qwen / MiMO，也不真实访问数据库：
 * {@link ExamMapper} / {@link QuestionMapper} / {@link FileParseService} /
 * {@link AiParseService} / {@link QwenAiParseService} 均用 Mockito mock；
 * {@link ObjectMapper} 用真实实例。</p>
 */
class AsyncParseServiceUserIdPropagationTest {

    private static final Long USER_ID = 77L;
    private static final Long EXAM_ID = 1001L;

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

    /**
     * 普通文本解析路径（parsePrecise=false + examType=writing）应把 userId 透传给
     * {@link AiParseService#parseWithAi(Long, String)}。
     *
     * <p>选择 examType="writing" 是为了让 {@code parseAndSave} 走 {@code parseSingle}
     * 分支（而不是 workflowParse），从而最直接地命中 {@code parseWithAi}。
     * 返回的题目用 "fill" 类型（非 "write"），避免触发 commitSection 内部的
     * writing-guidance 二次 AI 调用。</p>
     */
    @Test
    void parseAndSaveShouldPropagateUserIdToParseWithAi() throws Exception {
        // arrange: 关闭 Qwen 精准解析路径，强制走普通文本解析
        when(qwenAiParseService.isConfigured()).thenReturn(false);
        // 普通文本解析需要 aiParseService.isConfigured()=true 才会真正调用 parseWithAi
        when(aiParseService.isConfigured()).thenReturn(true);

        // extractedText 长度 >= 80，跳过 fileParseService.extractTextFromBytes
        String extractedText = "WRITING TASK 2\n\n" +
                "Write about the following topic: sample topic for unit testing userId propagation. " +
                "Give reasons for your answer and include any relevant examples. " +
                "Write at least 250 words.";

        // mock parseWithAi(userId, text) 返回一个含 fill 题目的结果（避免触发 write-guidance retry）
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("passages", List.of("sample passage text"));
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("questionNumber", 1);
        question.put("type", "fill");
        question.put("text", "Sample fill question text long enough");
        question.put("answer", "answer");
        question.put("explanation", "explanation");
        question.put("locatorText", "sample passage");
        parsed.put("questions", List.of(question));
        when(aiParseService.parseWithAi(eq(USER_ID), anyString())).thenReturn(parsed);

        // commitSection 会调用 examMapper.selectById 加载 Exam
        Exam exam = new Exam();
        exam.setId(EXAM_ID);
        exam.setUserId(USER_ID);
        exam.setType("writing");
        exam.setStatus("processing");
        when(examMapper.selectById(EXAM_ID)).thenReturn(exam);

        // act: 调用 parseAndSave（直接同步调用，绕过 Spring @Async proxy）
        service.parseAndSave(USER_ID, EXAM_ID, null, "test.pdf",
                false, extractedText, "writing");

        // assert: userId 被透传到 parseWithAi
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiParseService).parseWithAi(eq(USER_ID), textCaptor.capture());
        // 传入 parseWithAi 的文本应是我们提供的 extractedText（或其经过 findTestContent 的版本）
        String passedText = textCaptor.getValue();
        // extractedText 里有 "WRITING TASK"，findTestContent 不会截断它（marker 位置在头部 < 500）
        // 因此传入的文本应包含原始 extractedText 的核心内容
        org.junit.jupiter.api.Assertions.assertTrue(
                passedText.contains("WRITING TASK") || passedText.contains("sample topic"),
                "passed text should contain the extracted content");
    }
}
