package com.ieltsstudio.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link AiFeature} 单元测试。
 *
 * <p>验证每个功能的 taskType 与 builtinCost 与
 * {@code docs/security-and-quota-plan.md} §2.2 一致。</p>
 */
class AiFeatureTest {

    @Test
    void writingGradeShouldBeTextCost2() {
        assertEquals(AiTaskType.TEXT, AiFeature.WRITING_GRADE.getTaskType());
        assertEquals(2, AiFeature.WRITING_GRADE.getBuiltinCost());
    }

    @Test
    void aiChatShouldBeTextCost1() {
        assertEquals(AiTaskType.TEXT, AiFeature.AI_CHAT.getTaskType());
        assertEquals(1, AiFeature.AI_CHAT.getBuiltinCost());
    }

    @Test
    void translateShouldBeTextCost1() {
        assertEquals(AiTaskType.TEXT, AiFeature.TRANSLATE.getTaskType());
        assertEquals(1, AiFeature.TRANSLATE.getBuiltinCost());
    }

    @Test
    void clozeGenerateShouldBeTextCost2() {
        assertEquals(AiTaskType.TEXT, AiFeature.CLOZE_GENERATE.getTaskType());
        assertEquals(2, AiFeature.CLOZE_GENERATE.getBuiltinCost());
    }

    @Test
    void clozeCheckShouldBeTextCost1() {
        assertEquals(AiTaskType.TEXT, AiFeature.CLOZE_CHECK.getTaskType());
        assertEquals(1, AiFeature.CLOZE_CHECK.getBuiltinCost());
    }

    @Test
    void wordGenerateShouldBeTextCost2() {
        assertEquals(AiTaskType.TEXT, AiFeature.WORD_GENERATE.getTaskType());
        assertEquals(2, AiFeature.WORD_GENERATE.getBuiltinCost());
    }

    @Test
    void examParseShouldBeTextCost5() {
        assertEquals(AiTaskType.TEXT, AiFeature.EXAM_PARSE.getTaskType());
        assertEquals(5, AiFeature.EXAM_PARSE.getBuiltinCost());
    }

    @Test
    void examPreciseParseShouldBeVisionCost10() {
        assertEquals(AiTaskType.VISION, AiFeature.EXAM_PRECISE_PARSE.getTaskType());
        assertEquals(10, AiFeature.EXAM_PRECISE_PARSE.getBuiltinCost());
    }

    @Test
    void writingGuidanceShouldBeTextCost1() {
        assertEquals(AiTaskType.TEXT, AiFeature.WRITING_GUIDANCE.getTaskType());
        assertEquals(1, AiFeature.WRITING_GUIDANCE.getBuiltinCost());
    }

    @Test
    void headingExtractShouldBeTextCost1() {
        assertEquals(AiTaskType.TEXT, AiFeature.HEADING_EXTRACT.getTaskType());
        assertEquals(1, AiFeature.HEADING_EXTRACT.getBuiltinCost());
    }
}
