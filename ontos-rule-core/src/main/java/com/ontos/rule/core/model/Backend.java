package com.ontos.rule.core.model;

/**
 * 执行后端类型。
 *
 * <p>AUTO 表示由 BackendRouter 根据数据规模自动选择。
 */
public enum Backend {
    /** 自动选择（基于数据规模 + 数据源类型） */
    AUTO,
    /** Java 内存求值，适合单行校验或 < 10 万行 */
    JVM,
    /** SQL 下推执行，适合 10 万 ~ 10 亿行 */
    SQL,
    /** Spark 分布式执行，适合 > 10 亿行（Phase 2） */
    SPARK
}
