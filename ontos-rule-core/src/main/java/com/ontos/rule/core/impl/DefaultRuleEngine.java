package com.ontos.rule.core.impl;

import com.ontos.rule.core.RuleEngine;
import com.ontos.rule.core.backend.jvm.JvmBackend;
import com.ontos.rule.core.backend.sql.SqlBackend;
import com.ontos.rule.core.backend.sql.dialect.SqlDialects;
import com.ontos.rule.core.compiler.CelCompiler;
import com.ontos.rule.core.invocation.InMemoryInvocationRecorder;
import com.ontos.rule.core.invocation.InvocationRecorder;
import com.ontos.rule.core.model.Backend;
import com.ontos.rule.core.model.CompiledRule;
import com.ontos.rule.core.model.ExecutionHints;
import com.ontos.rule.core.model.InvocationContext;
import com.ontos.rule.core.model.InvocationRecord;
import com.ontos.rule.core.model.ViolationResult;
import com.ontos.rule.core.router.BackendRouter;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认的 RuleEngine 实现。
 *
 * <p>装配关系：
 * <pre>
 *   DefaultRuleEngine
 *     ├── CelCompiler     编译 CEL → AST
 *     ├── JvmBackend      JVM 求值
 *     ├── BackendRouter   选 Backend (Phase 1 只走 JVM)
 *     └── InvocationRecorder 每次调用自动记录
 * </pre>
 *
 * <p>通过 Builder 构建，所有依赖可注入。
 */
public class DefaultRuleEngine implements RuleEngine {

    private final CelCompiler compiler;
    private final JvmBackend jvmBackend;
    private final BackendRouter router;
    private final InvocationRecorder recorder;
    private final String defaultCaller;
    /** 按方言缓存 SqlBackend 实例（每个方言 1 个，复用） */
    private final Map<String, SqlBackend> sqlBackendCache = new ConcurrentHashMap<>();

    private DefaultRuleEngine(Builder b) {
        this.compiler = b.compiler != null ? b.compiler : new CelCompiler();
        this.jvmBackend = b.jvmBackend != null ? b.jvmBackend : new JvmBackend();
        this.router = b.router != null ? b.router : new BackendRouter();
        this.recorder = b.recorder != null ? b.recorder : new InMemoryInvocationRecorder();
        this.defaultCaller = b.caller != null ? b.caller : "anonymous";
    }

    public static Builder builder() {
        return new Builder();
    }

    // ===== 编译 =====
    @Override
    public CompiledRule compile(String expression) {
        return compiler.compile(expression);
    }

    @Override
    public CompiledRule compile(String expression, Set<String> declaredVars) {
        return compiler.compile(expression, declaredVars);
    }

    // ===== 单条求值 =====
    @Override
    public boolean eval(CompiledRule rule, Map<String, Object> record) {
        return eval(rule, record, InvocationContext.of(defaultCaller));
    }

    @Override
    public boolean eval(CompiledRule rule, Map<String, Object> record, InvocationContext ctx) {
        Instant start = Instant.now();
        Boolean result = null;
        String error = null;
        try {
            result = jvmBackend.eval(rule, record);
            return result;
        } catch (RuntimeException e) {
            error = e.getMessage();
            throw e;
        } finally {
            recordInvocation(
                ctx, rule.expression(), record.toString(),
                InvocationRecord.Mode.EVAL,
                String.valueOf(result),
                Backend.JVM,
                Duration.between(start, Instant.now()),
                error
            );
        }
    }

    // ===== 批量执行 =====
    @Override
    public ViolationResult execute(CompiledRule rule,
                                   Iterable<Map<String, Object>> rows,
                                   ExecutionHints hints) {
        return execute(rule, rows, hints, InvocationContext.of(defaultCaller));
    }

    @Override
    public ViolationResult execute(CompiledRule rule,
                                   Iterable<Map<String, Object>> rows,
                                   ExecutionHints hints,
                                   InvocationContext ctx) {
        Instant start = Instant.now();
        ViolationResult result = null;
        String error = null;
        try {
            // Phase 1: 只走 JVM。SQL/SPARK 路由后由 SqlBackend / SparkBackend 接管
            Backend chosen = router.route(rule, -1, hints);
            if (chosen != Backend.JVM) {
                throw new UnsupportedOperationException(
                    "Backend " + chosen + " 尚未实现批量执行 · Phase 2 完成 SQL Backend"
                );
            }
            result = jvmBackend.executeBatch(rule, rows, hints);
            return result;
        } catch (RuntimeException e) {
            error = e.getMessage();
            throw e;
        } finally {
            String summary = result != null
                ? result.violationCount() + " violations / " + result.totalRows() + " rows"
                : "FAILED";
            recordInvocation(
                ctx, rule.expression(), "iterable<Map>",
                InvocationRecord.Mode.EXECUTE,
                summary,
                Backend.JVM,
                Duration.between(start, Instant.now()),
                error
            );
        }
    }

    // ===== SQL Backend =====
    @Override
    public ViolationResult executeOnSql(CompiledRule rule, DataSource ds, String tableName,
                                         String dialectName, ExecutionHints hints) {
        return executeOnSql(rule, ds, tableName, dialectName, hints,
            InvocationContext.of(defaultCaller));
    }

    @Override
    public ViolationResult executeOnSql(CompiledRule rule, DataSource ds, String tableName,
                                         String dialectName, ExecutionHints hints,
                                         InvocationContext ctx) {
        SqlBackend backend = sqlBackendCache.computeIfAbsent(
            dialectName, d -> new SqlBackend(SqlDialects.of(d))
        );

        Instant start = Instant.now();
        ViolationResult result = null;
        String error = null;
        try {
            result = backend.executeBatch(rule, ds, tableName, hints);
            return result;
        } catch (RuntimeException e) {
            error = e.getMessage();
            throw e;
        } finally {
            String summary = result != null
                ? result.violationCount() + " violations / " + result.totalRows() + " rows"
                : "FAILED";
            recordInvocation(
                ctx, rule.expression(),
                "jdbc:" + dialectName + " · table=" + tableName,
                InvocationRecord.Mode.EXECUTE,
                summary,
                Backend.SQL,
                Duration.between(start, Instant.now()),
                error
            );
        }
    }

    @Override
    public InvocationRecorder recorder() {
        return recorder;
    }

    // ===== 内部 =====
    private void recordInvocation(InvocationContext ctx,
                                  String expr,
                                  String input,
                                  InvocationRecord.Mode mode,
                                  String result,
                                  Backend backend,
                                  Duration latency,
                                  String error) {
        InvocationRecord r = new InvocationRecord(
            UUID.randomUUID().toString().substring(0, 8),
            ctx, expr, input, mode, result, backend, latency, Instant.now(), error
        );
        recorder.record(r);
    }

    // ===== Builder =====
    public static class Builder {
        private CelCompiler compiler;
        private JvmBackend jvmBackend;
        private BackendRouter router;
        private InvocationRecorder recorder;
        private String caller;

        public Builder caller(String c) { this.caller = c; return this; }
        public Builder recorder(InvocationRecorder r) { this.recorder = r; return this; }
        public Builder compiler(CelCompiler c) { this.compiler = c; return this; }
        public Builder jvmBackend(JvmBackend b) { this.jvmBackend = b; return this; }
        public Builder router(BackendRouter r) { this.router = r; return this; }

        public DefaultRuleEngine build() {
            return new DefaultRuleEngine(this);
        }
    }
}
