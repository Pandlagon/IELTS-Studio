package com.ieltsstudio.ai;

/**
 * AI 任务类型枚举。
 *
 * <ul>
 *   <li>{@link #TEXT} —— 文本任务：写作评分、AI Chat、翻译、普通解析、完形填空。</li>
 *   <li>{@link #VISION} —— 多模态任务：PDF / 图片精准解析、扫描版试卷、图表识别。</li>
 * </ul>
 */
public enum AiTaskType {
    TEXT,
    VISION
}
