package com.ieltsstudio.ai;

/**
 * AI 功能枚举，用于 {@link com.ieltsstudio.ai.service.AiUsageGuard} 的扣费 / 限流映射。
 *
 * <p>对应 {@code docs/security-and-quota-plan.md} 中需保护的接口列表。</p>
 */
public enum AiFeature {
    /** 写作评分（/exams/grade-writing） */
    WRITING_GRADE,
    /** AI 助手问答（/exams/ai-chat） */
    AI_CHAT,
    /** 划词翻译（/exams/translate） */
    TRANSLATE,
    /** 完形填空生成（/words/cloze/generate） */
    CLOZE_GENERATE,
    /** 完形填空批改（/words/cloze/check） */
    CLOZE_CHECK,
    /** 普通试卷解析（/exams/upload 普通模式） */
    EXAM_PARSE,
    /** 精准视觉解析（/exams/upload 精准模式，走 Vision Provider） */
    EXAM_PRECISE_PARSE
}
