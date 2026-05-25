package com.ontos.rule.core.backend.jvm;

import com.ontos.rule.core.backend.ExecutionBackend;
import com.ontos.rule.core.model.Backend;
import com.ontos.rule.core.model.CompiledRule;
import com.ontos.rule.core.model.ExecutionHints;
import com.ontos.rule.core.model.ViolationResult;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JVM 内存执行 Backend。
 *
 * <p>用 dev.cel 的原生 runtime 求值。
 *
 * <p>性能预期：
 * <ul>
 *   <li>单条求值: < 200μs</li>
 *   <li>单核 op/s: ~50 万</li>
 * </ul>
 *
 * <p>适用场景：
 * <ul>
 *   <li>写入时校验（Action preconditions）</li>
 *   <li>小数据批量（&lt; 10 万行）</li>
 *   <li>规则单元测试</li>
 * </ul>
 */
public class JvmBackend implements ExecutionBackend {

    private final CelRuntime runtime;
    /** 缓存 program 对象（避免每次 eval 都重新创建） */
    private final Map<String, CelRuntime.Program> programCache = new ConcurrentHashMap<>();

    public JvmBackend() {
        this.runtime = CelRuntimeFactory.standardCelRuntimeBuilder().build();
    }

    @Override
    public Backend kind() {
        return Backend.JVM;
    }

    @Override
    public boolean eval(CompiledRule rule, Map<String, Object> record) {
        CelRuntime.Program program = programCache.computeIfAbsent(
            rule.expression(),
            k -> createProgram(rule)
        );
        try {
            // 规则用了 time_within 等时间函数时，注入 _now（当前时间）
            Map<String, Object> ctx = record;
            if (rule.variables() != null && rule.variables().contains("_now")) {
                ctx = new HashMap<>(record);
                ctx.put("_now", Instant.now());
            }
            Object result = program.eval(ctx);
            return Boolean.TRUE.equals(result);
        } catch (CelEvaluationException e) {
            throw new RuntimeException(
                "CEL 求值失败: " + e.getMessage() + " · 表达式: " + rule.expression(), e
            );
        }
    }

    private CelRuntime.Program createProgram(CompiledRule rule) {
        try {
            return runtime.createProgram(rule.ast());
        } catch (CelEvaluationException e) {
            throw new RuntimeException(
                "CEL Program 创建失败: " + e.getMessage() + " · 表达式: " + rule.expression(), e
            );
        }
    }

    /**
     * 批量执行——JVM Backend 接收 Iterable&lt;Map&gt; 作为数据源。
     */
    public ViolationResult executeBatch(CompiledRule rule,
                                        Iterable<Map<String, Object>> rows,
                                        ExecutionHints hints) {
        Instant start = Instant.now();
        long total = 0;
        long violations = 0;
        List<Map<String, Object>> samples = new ArrayList<>();
        int sampleLimit = hints.sampleLimit();

        for (Map<String, Object> row : rows) {
            total++;
            boolean ok = eval(rule, row);
            if (!ok) {
                violations++;
                if (samples.size() < sampleLimit) {
                    samples.add(row);
                }
            }
            if (Duration.between(start, Instant.now()).compareTo(hints.timeout()) > 0) {
                throw new RuntimeException(
                    "JVM Backend 执行超时 · 已处理 " + total + " 行 · 超时阈值 " + hints.timeout()
                );
            }
        }

        return new ViolationResult(
            total, violations, samples, Backend.JVM,
            Duration.between(start, Instant.now())
        );
    }

    /** 清空 program 缓存（规则更新时调用） */
    public void invalidateCache() {
        programCache.clear();
    }
}
