package com.ieltsstudio.ai;

import lombok.Getter;

/**
 * AI 功能枚举，用于 {@link com.ieltsstudio.ai.service.AiUsageGuard} 的扣费 / 限流映射。
 *
 * <p>每个功能绑定一个 {@link AiTaskType} 与内置模式下的扣费 cost，
 * cost 取值与 {@code docs/security-and-quota-plan.md} §2.2 一致。</p>
 */
@Getter
public enum AiFeature {

    /** 写作评分（/exams/grade-writing），文本任务，cost=2 */
    WRITING_GRADE(AiTaskType.TEXT, 2),

    /** AI 助手问答（/exams/ai-chat），文本任务，cost=1 */
    AI_CHAT(AiTaskType.TEXT, 1),

    /** 划词翻译（/exams/translate），文本任务，cost=1 */
    TRANSLATE(AiTaskType.TEXT, 1),

    /** 完形填空生成（/words/cloze/generate），文本任务，cost=2 */
    CLOZE_GENERATE(AiTaskType.TEXT, 2),

    /** 完形填空批改（/words/cloze/check），文本任务，cost=1 */
    CLOZE_CHECK(AiTaskType.TEXT, 1),

    /** 普通试卷解析（/exams/upload 普通模式），文本任务，cost=5 */
    EXAM_PARSE(AiTaskType.TEXT, 5),

    /** 精准视觉解析（/exams/upload 精准模式，走 Vision Provider），多模态任务，cost=10 */
    EXAM_PRECISE_PARSE(AiTaskType.VISION, 10);

    /** 该功能所属的任务类型 */
    private final AiTaskType taskType;

    /** 内置 Key 模式下调用成功后扣减的 credits，自填 Key 模式不消耗站点 credits */
    private final int builtinCost;

    AiFeature(AiTaskType taskType, int builtinCost) {
        this.taskType = taskType;
        this.builtinCost = builtinCost;
    }
}
