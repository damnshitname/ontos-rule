package com.ontos.rule.core.model;

import java.time.Duration;
import java.time.Instant;

/**
 * 单次 core API 调用的追溯记录。
 *
 * <p>所有调用（eval / execute）都会生成一条 InvocationRecord，
 * 由 InvocationRecorder 持久化（默认 in-memory，可注入外部实现）。
 *
 * @param id          调用 ID（短 UUID）
 * @param context     调用上下文（含 caller / user / traceId）
 * @param expression  CEL 表达式
 * @param input       输入数据（eval 模式是 Map 序列化字符串；execute 模式是数据源描述）
 * @param mode        调用模式（EVAL 单条 / EXECUTE 批量）
 * @param result      结果摘要（"true" / "false" / "142 violations" / "ERROR: ..."）
 * @param backend     实际使用的 Backend
 * @param latency     执行耗时
 * @param timestamp   调用时间
 * @param error       错误信息（null 表示成功）
 */
public record InvocationRecord(
    String id,
    InvocationContext context,
    String expression,
    String input,
    Mode mode,
    String result,
    Backend backend,
    Duration latency,
    Instant timestamp,
    String error
) {
    public enum Mode { EVAL, EXECUTE }

    public boolean isSuccess() {
        return error == null;
    }
}
