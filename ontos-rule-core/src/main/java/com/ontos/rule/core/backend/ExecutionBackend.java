package com.ontos.rule.core.backend;

import com.ontos.rule.core.model.Backend;
import com.ontos.rule.core.model.CompiledRule;
import com.ontos.rule.core.model.ExecutionHints;
import com.ontos.rule.core.model.ViolationResult;

import java.util.Map;

/**
 * 执行后端接口。
 *
 * <p>所有 Backend（JVM / SQL / Spark）实现此接口。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>eval</b>：单行求值，所有 Backend 都必须支持（用于写入时校验场景）</li>
 *   <li><b>executeBatch</b>：批量执行，不同 Backend 接受不同数据源类型，
 *       默认抛 UnsupportedOperationException</li>
 * </ul>
 */
public interface ExecutionBackend {

    /** Backend 类型 */
    Backend kind();

    /**
     * 单行求值。
     *
     * @param rule    已编译的规则
     * @param record  数据记录（字段名 → 值）
     * @return  true 表示通过；false 表示违规
     */
    boolean eval(CompiledRule rule, Map<String, Object> record);

    /**
     * 批量执行——子类按数据源类型决定如何实现。
     *
     * <p>例如 JvmBackend 支持 {@code executeBatch(rule, Iterable<Map>, hints)}，
     * SqlBackend 支持 {@code executeBatch(rule, DataSource, table, hints)}。
     *
     * <p>默认实现抛 UnsupportedOperationException——这是个标记接口，
     * 实际批量执行 API 由子类按数据源类型定义。
     */
    default ViolationResult executeBatch(CompiledRule rule, Object target, ExecutionHints hints) {
        throw new UnsupportedOperationException(
            "Backend " + kind() + " 未实现批量执行 · target type: " + target.getClass().getName()
        );
    }
}
