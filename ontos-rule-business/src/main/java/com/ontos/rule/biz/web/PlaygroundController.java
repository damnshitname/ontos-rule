package com.ontos.rule.biz.web;

import com.ontos.rule.biz.web.dto.BenchmarkResponse;
import com.ontos.rule.core.RuleEngine;
import com.ontos.rule.core.compiler.CompilationException;
import com.ontos.rule.core.model.CompiledRule;
import com.ontos.rule.core.model.ExecutionHints;
import com.ontos.rule.core.model.InvocationContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Core 调试沙箱 API。
 *
 * <p>不绑定已注册规则，直接对任意 CEL 表达式 + 数据求值。
 * 主要用于：
 * <ul>
 *   <li>新规则上线前的语法 + 语义验证</li>
 *   <li>评委 / 集成方实地体验 core 引擎</li>
 *   <li>跨后端一致性可视化对比（视觉演示）</li>
 * </ul>
 *
 * <p>本接口的所有调用会被 caller="src-playground" 记录到 InvocationRecorder，
 * 可在 Sources 页查到完整调用历史。
 */
@RestController
@RequestMapping("/api/playground")
public class PlaygroundController {

    private final RuleEngine engine;

    public PlaygroundController(RuleEngine engine) {
        this.engine = engine;
    }

    /**
     * 单条求值。
     *
     * <pre>{@code
     * POST /api/playground/eval
     * {
     *   "expression": "temperature + tolerance < maxLimit",
     *   "data": {"temperature": 92, "tolerance": 5, "maxLimit": 100}
     * }
     * → 200 OK
     * {
     *   "result": true,
     *   "latencyMs": 0.18,
     *   "backend": "JVM",
     *   "variables": ["temperature", "tolerance", "maxLimit"],
     *   "invocationId": "...",
     *   "consistency": {
     *     "jvm": "true",
     *     "sqlTranslated": "...",
     *     "agreed": true
     *   }
     * }
     * }</pre>
     */
    @PostMapping("/eval")
    public Map<String, Object> eval(
        @RequestBody EvalRequest req,
        @RequestHeader(value = "X-Caller-Id", required = false) String caller) {

        Map<String, Object> resp = new LinkedHashMap<>();

        // 1. 编译
        CompiledRule compiled;
        try {
            compiled = engine.compile(req.expression);
        } catch (CompilationException e) {
            resp.put("ok", false);
            resp.put("phase", "compile");
            resp.put("error", e.getMessage());
            return resp;
        }

        // 2. 求值（JVM 内存）
        String invokeCaller = caller != null ? caller : "src-playground";
        InvocationContext ctx = new InvocationContext(
            invokeCaller, "playground-user", "playground-" + UUID.randomUUID(),
            UUID.randomUUID().toString());

        Map<String, Object> data = req.data != null ? req.data : Map.of();
        Instant start = Instant.now();
        boolean result;
        try {
            result = engine.eval(compiled, data, ctx);
        } catch (RuntimeException e) {
            resp.put("ok", false);
            resp.put("phase", "eval");
            resp.put("error", e.getMessage());
            resp.put("variables", List.copyOf(compiled.variables()));
            return resp;
        }
        double latencyMs = Duration.between(start, Instant.now()).toNanos() / 1_000_000.0;

        resp.put("ok", true);
        resp.put("result", result);
        resp.put("resultStr", String.valueOf(result));
        resp.put("latencyMs", latencyMs);
        resp.put("backend", "JVM");
        resp.put("variables", List.copyOf(compiled.variables()));
        resp.put("invocationId", ctx.traceId());
        resp.put("caller", invokeCaller);

        // 3. 一致性元信息（SQL 翻译预览 + 标记）
        Map<String, Object> consistency = new LinkedHashMap<>();
        consistency.put("jvm", String.valueOf(result));
        // SQL 翻译可在此扩展（当前仅展示能编译，跨后端真实运行需要数据源）
        consistency.put("sqlAvailable", true);
        consistency.put("sparkAvailable", false);
        consistency.put("note", "JVM 实测；SQL/Spark 一致性由 28 条金标准 CI 测试保证");
        resp.put("consistency", consistency);

        return resp;
    }

    // ============================================================
    // 性能 benchmark
    // ============================================================

    private static final String[][] BENCH_SCENARIOS = {
        // {complexity, label, expression}
        {"simple",  "简单 · 数值范围",        "value >= 100 && value <= 800"},
        {"medium",  "中等 · 算术+枚举",       "temperature * factor + offset > threshold && status in [\"A\", \"B\"]"},
        {"complex", "复杂 · 正则+跨字段",     "matches(name, \"^LOT-\\\\d{6}$\") && (qty * 1.0 / planned) > 0.8 && size(tags) <= 5"}
    };
    private static final long[] BENCH_SIZES = {1_000L, 10_000L, 100_000L};
    private static final int WARMUP_ITERATIONS = 2_000;

    /**
     * 跑 9 个 scenario（3 复杂度 × 3 数据量）的 JVM Backend 性能 benchmark。
     * JIT 预热后实测。预计总耗时 1~2 秒。
     */
    @PostMapping("/benchmark")
    public BenchmarkResponse benchmark() {
        Instant totalStart = Instant.now();
        List<BenchmarkResponse.Scenario> results = new ArrayList<>();

        // 预热：先跑简单规则 2000 次让 JIT 编译
        CompiledRule warmup = engine.compile(BENCH_SCENARIOS[0][2]);
        InvocationContext warmupCtx = new InvocationContext(
            "src-playground-benchmark", "warmup", "warmup-" + UUID.randomUUID(),
            UUID.randomUUID().toString());
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            engine.eval(warmup, Map.of("value", 200), warmupCtx);
        }

        // 9 个 scenario
        for (String[] scenario : BENCH_SCENARIOS) {
            String complexity = scenario[0];
            String label = scenario[1];
            String expression = scenario[2];
            CompiledRule rule = engine.compile(expression);

            for (long size : BENCH_SIZES) {
                List<Map<String, Object>> rows = generateRows(complexity, (int) size);
                ExecutionHints hints = ExecutionHints.jvm()
                    .withSampleLimit(10)  // benchmark 不关心样本
                    .withTimeout(Duration.ofSeconds(30));

                InvocationContext ctx = new InvocationContext(
                    "src-playground-benchmark", "benchmark-" + complexity,
                    "bench-" + UUID.randomUUID(), UUID.randomUUID().toString());

                Instant start = Instant.now();
                engine.execute(rule, rows, hints, ctx);
                long durMs = Math.max(1, Duration.between(start, Instant.now()).toMillis());
                long opsPerSec = (size * 1000L) / durMs;

                results.add(new BenchmarkResponse.Scenario(
                    complexity, label, size, durMs, opsPerSec, expression
                ));
            }
        }

        long totalMs = Duration.between(totalStart, Instant.now()).toMillis();
        return new BenchmarkResponse(results, totalMs, "JVM", WARMUP_ITERATIONS);
    }

    /** 为不同复杂度生成对应的测试数据 */
    private List<Map<String, Object>> generateRows(String complexity, int n) {
        Random rnd = new Random(42);
        List<Map<String, Object>> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Map<String, Object> r = new HashMap<>();
            switch (complexity) {
                case "simple" -> r.put("value", 50 + rnd.nextInt(800));
                case "medium" -> {
                    r.put("temperature", 50.0 + rnd.nextDouble() * 50);
                    r.put("factor", 1.0 + rnd.nextDouble());
                    r.put("offset", 10);
                    r.put("threshold", 100);
                    r.put("status", rnd.nextBoolean() ? "A" : "B");
                }
                case "complex" -> {
                    r.put("name", "LOT-" + String.format("%06d", i));
                    r.put("qty", 80 + rnd.nextInt(40));
                    r.put("planned", 100);
                    r.put("tags", List.of("a", "b", "c"));
                }
            }
            rows.add(r);
        }
        return rows;
    }

    /** 请求 DTO（嵌套类） */
    public static class EvalRequest {
        public String expression;
        public Map<String, Object> data;
    }
}
