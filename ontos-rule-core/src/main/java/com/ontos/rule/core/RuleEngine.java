package com.ontos.rule.core;

import com.ontos.rule.core.invocation.InvocationRecorder;
import com.ontos.rule.core.model.CompiledRule;
import com.ontos.rule.core.model.ExecutionHints;
import com.ontos.rule.core.model.InvocationContext;
import com.ontos.rule.core.model.ViolationResult;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Set;

/**
 * 规则引擎主入口。
 *
 * <p>典型用法：
 * <pre>{@code
 * RuleEngine engine = DefaultRuleEngine.builder()
 *     .caller("src-my-platform")
 *     .build();
 *
 * // 编译一次（缓存复用）
 * CompiledRule rule = engine.compile("a + b < c");
 *
 * // 单条求值
 * boolean ok = engine.eval(rule, Map.of("a", 1, "b", 2, "c", 5));
 *
 * // 批量执行
 * ViolationResult result = engine.execute(rule, dataRows, ExecutionHints.auto());
 * }</pre>
 */
public interface RuleEngine {

    // ============= 编译 =============

    /**
     * 编译 CEL 表达式，自动从表达式中提取变量名。
     */
    CompiledRule compile(String expression);

    /**
     * 编译 CEL 表达式，使用显式声明的变量列表。
     */
    CompiledRule compile(String expression, Set<String> declaredVars);

    // ============= 单条求值 =============

    /**
     * 单条求值（使用默认 caller）。
     */
    boolean eval(CompiledRule rule, Map<String, Object> record);

    /**
     * 单条求值（显式传入调用上下文）。
     */
    boolean eval(CompiledRule rule, Map<String, Object> record, InvocationContext ctx);

    // ============= 批量执行 =============

    /**
     * 批量执行——JVM Backend，接收 Iterable&lt;Map&gt; 作为数据源。
     */
    ViolationResult execute(CompiledRule rule,
                            Iterable<Map<String, Object>> rows,
                            ExecutionHints hints);

    /**
     * 批量执行（显式传入调用上下文）。
     */
    ViolationResult execute(CompiledRule rule,
                            Iterable<Map<String, Object>> rows,
                            ExecutionHints hints,
                            InvocationContext ctx);

    // ============= SQL Backend（批量下推执行）=============

    /**
     * 批量执行——SQL Backend，把表达式翻译为 SQL 下推到数据库执行。
     *
     * @param rule          已编译规则
     * @param dataSource    JDBC DataSource
     * @param tableName     目标表名
     * @param dialectName   方言名（postgresql / mysql / oracle / hive / impala / starrocks）
     * @param hints         执行提示
     */
    ViolationResult executeOnSql(CompiledRule rule,
                                  DataSource dataSource,
                                  String tableName,
                                  String dialectName,
                                  ExecutionHints hints);

    /**
     * 同上，显式传入调用上下文。
     */
    ViolationResult executeOnSql(CompiledRule rule,
                                  DataSource dataSource,
                                  String tableName,
                                  String dialectName,
                                  ExecutionHints hints,
                                  InvocationContext ctx);

    // ============= 调用追溯 =============

    /**
     * 获取调用记录器，用于查询历史调用、按来源分组等。
     */
    InvocationRecorder recorder();
}
