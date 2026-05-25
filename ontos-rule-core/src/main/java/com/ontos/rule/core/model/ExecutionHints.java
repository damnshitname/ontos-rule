package com.ontos.rule.core.model;

import java.time.Duration;

/**
 * 批量执行的提示参数。
 *
 * @param backend       期望使用的 Backend（AUTO 表示自动选择）
 * @param sampleLimit   违规记录采样上限（总数始终精确，仅样本被截断）
 * @param timeout       执行超时
 */
public record ExecutionHints(
    Backend backend,
    int sampleLimit,
    Duration timeout
) {
    /** 默认：自动选 Backend，采样 1000 条，超时 10 分钟 */
    public static ExecutionHints auto() {
        return new ExecutionHints(Backend.AUTO, 1000, Duration.ofMinutes(10));
    }

    /** 强制 JVM Backend */
    public static ExecutionHints jvm() {
        return new ExecutionHints(Backend.JVM, 1000, Duration.ofMinutes(10));
    }

    /** 强制 SQL Backend */
    public static ExecutionHints sql() {
        return new ExecutionHints(Backend.SQL, 1000, Duration.ofMinutes(10));
    }

    public ExecutionHints withSampleLimit(int n) {
        return new ExecutionHints(backend, n, timeout);
    }

    public ExecutionHints withTimeout(Duration t) {
        return new ExecutionHints(backend, sampleLimit, t);
    }
}
