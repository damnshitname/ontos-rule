package com.ontos.rule.core.model;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 批量执行结果。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>违规总数始终精确</b>——不管数据多大都给真实计数</li>
 *   <li><b>违规样本受 sampleLimit 限制</b>——避免结果集爆表</li>
 * </ul>
 *
 * @param totalRows       扫描的总行数
 * @param violationCount  违规行数（精确）
 * @param samples         违规样本（最多 hints.sampleLimit 条）
 * @param backendUsed     实际使用的 Backend
 * @param elapsed         实际耗时
 */
public record ViolationResult(
    long totalRows,
    long violationCount,
    List<Map<String, Object>> samples,
    Backend backendUsed,
    Duration elapsed
) {
    public double violationRate() {
        return totalRows == 0 ? 0.0 : (double) violationCount / totalRows;
    }

    public boolean hasViolations() {
        return violationCount > 0;
    }
}
