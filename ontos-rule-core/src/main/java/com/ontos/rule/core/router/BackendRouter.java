package com.ontos.rule.core.router;

import com.ontos.rule.core.model.Backend;
import com.ontos.rule.core.model.CompiledRule;
import com.ontos.rule.core.model.ExecutionHints;

/**
 * Backend 自动路由。
 *
 * <p>根据数据规模 + 用户 hint，决定用哪个 Backend 执行规则。
 *
 * <p>路由策略（默认）：
 * <table border="1">
 *   <tr><th>条件</th><th>选择</th></tr>
 *   <tr><td>hint != AUTO</td><td>遵从用户 hint</td></tr>
 *   <tr><td>预估行数 &lt; 10 万</td><td>JVM</td></tr>
 *   <tr><td>10 万 ~ 10 亿</td><td>SQL</td></tr>
 *   <tr><td>&gt; 10 亿</td><td>SPARK (Phase 2)</td></tr>
 * </table>
 */
public class BackendRouter {

    private static final long JVM_THRESHOLD = 100_000L;
    private static final long SQL_THRESHOLD = 1_000_000_000L;

    /**
     * 根据规则 + 数据规模 + 提示选择 Backend。
     *
     * @param rule            已编译规则（暂未用，预留给"基于表达式特征选 Backend"）
     * @param estimatedRows   预估扫描行数（-1 表示未知，默认用 JVM）
     * @param hints           执行提示
     * @return  选定的 Backend
     */
    public Backend route(CompiledRule rule, long estimatedRows, ExecutionHints hints) {
        if (hints.backend() != Backend.AUTO) {
            return hints.backend();
        }
        if (estimatedRows < 0 || estimatedRows < JVM_THRESHOLD) {
            return Backend.JVM;
        }
        if (estimatedRows < SQL_THRESHOLD) {
            return Backend.SQL;
        }
        return Backend.SPARK;
    }
}
