package com.ieltsstudio.entity;

/**
 * 管理端审计日志操作状态枚举（Phase 8D）。
 *
 * <p>Phase 8D 至少记录成功写操作（{@link #SUCCESS}）。
 * 失败审计（{@link #FAILED}）可先只记录已经捕获的业务异常场景；
 * 如果接入成本太高，本阶段允许只记录成功写操作，但文档必须明确说明。</p>
 */
public enum AdminOperationStatus {

    /** 操作成功 */
    SUCCESS,

    /** 操作失败（已捕获的业务异常） */
    FAILED
}
