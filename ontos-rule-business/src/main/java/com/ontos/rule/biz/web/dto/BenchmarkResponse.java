package com.ontos.rule.biz.web.dto;

import java.util.List;

/**
 * Playground benchmark 结果 —— 验证 PPT 性能数据。
 *
 * <p>9 个 scenario = 3 种复杂度 × 3 种数据量（1K / 10K / 100K）。
 * 全部在 JVM Backend 上跑，JIT 预热后实测。
 */
public record BenchmarkResponse(
    List<Scenario> results,
    long totalElapsedMs,
    String backend,
    int warmupIterations
) {
    public record Scenario(
        String complexity,    // "simple" / "medium" / "complex"
        String complexityLabel,  // 中文标签
        long rowCount,
        long durationMs,
        long opsPerSec,
        String expression
    ) {}
}
