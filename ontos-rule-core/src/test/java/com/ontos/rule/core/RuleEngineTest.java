package com.ontos.rule.core;

import com.ontos.rule.core.compiler.CompilationException;
import com.ontos.rule.core.impl.DefaultRuleEngine;
import com.ontos.rule.core.model.Backend;
import com.ontos.rule.core.model.CompiledRule;
import com.ontos.rule.core.model.ExecutionHints;
import com.ontos.rule.core.model.InvocationContext;
import com.ontos.rule.core.model.InvocationRecord;
import com.ontos.rule.core.model.ViolationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RuleEngine 集成测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>简单算术表达式（A + B &lt; C）</li>
 *   <li>复合表达式（算术 + in 列表 + 字符串比较）</li>
 *   <li>变量名自动提取</li>
 *   <li>调用追溯记录</li>
 *   <li>批量执行 + 违规采样</li>
 *   <li>编译异常</li>
 * </ul>
 */
class RuleEngineTest {

    private RuleEngine engine;

    @BeforeEach
    void setup() {
        engine = DefaultRuleEngine.builder()
            .caller("src-junit-test")
            .build();
    }

    @Test
    @DisplayName("A + B < C 简单算术——CEL 基础能力")
    void simpleArithmetic() {
        CompiledRule rule = engine.compile("a + b < c");

        assertThat(engine.eval(rule, Map.of("a", 1L, "b", 2L, "c", 5L))).isTrue();
        assertThat(engine.eval(rule, Map.of("a", 3L, "b", 3L, "c", 5L))).isFalse();
    }

    @Test
    @DisplayName("复合表达式——算术 + in 列表 + 字符串比较")
    void complexExpression() {
        CompiledRule rule = engine.compile(
            "temperature + tolerance < maxLimit && status in [\"RUNNING\",\"IDLE\"]"
        );

        boolean ok = engine.eval(rule, Map.of(
            "temperature", 92L, "tolerance", 5L, "maxLimit", 100L, "status", "RUNNING"
        ));
        assertThat(ok).isTrue();

        boolean fail = engine.eval(rule, Map.of(
            "temperature", 95L, "tolerance", 8L, "maxLimit", 100L, "status", "RUNNING"
        ));
        assertThat(fail).isFalse();

        boolean failStatus = engine.eval(rule, Map.of(
            "temperature", 50L, "tolerance", 5L, "maxLimit", 100L, "status", "DOWN"
        ));
        assertThat(failStatus).isFalse();
    }

    @Test
    @DisplayName("编译时自动提取变量名")
    void compileExtractsVariables() {
        CompiledRule rule = engine.compile("temperature + tolerance < maxLimit");
        assertThat(rule.variables()).contains("temperature", "tolerance", "maxLimit");
    }

    @Test
    @DisplayName("每次 eval 自动记录 invocation")
    void evalRecordsInvocation() {
        CompiledRule rule = engine.compile("x > 0");
        engine.eval(rule, Map.of("x", 5L));
        engine.eval(rule, Map.of("x", -1L));

        List<InvocationRecord> recent = engine.recorder().recent("src-junit-test", 10);
        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).context().caller()).isEqualTo("src-junit-test");
        assertThat(recent.get(0).mode()).isEqualTo(InvocationRecord.Mode.EVAL);
        assertThat(recent.get(0).backend()).isEqualTo(Backend.JVM);
        assertThat(recent.get(0).isSuccess()).isTrue();
    }

    @Test
    @DisplayName("显式 caller 覆盖默认 caller——多平台来源场景")
    void explicitCallerOverridesDefault() {
        CompiledRule rule = engine.compile("x > 0");
        engine.eval(rule, Map.of("x", 1L), InvocationContext.of("src-custom-platform"));

        List<InvocationRecord> custom = engine.recorder().recent("src-custom-platform", 10);
        List<InvocationRecord> def = engine.recorder().recent("src-junit-test", 10);

        assertThat(custom).hasSize(1);
        assertThat(def).hasSize(0);
        assertThat(custom.get(0).context().caller()).isEqualTo("src-custom-platform");
    }

    @Test
    @DisplayName("批量执行 + 违规采样——质量检测主场景")
    void executeOnIterable() {
        CompiledRule rule = engine.compile("value >= 0 && value <= 100");
        List<Map<String, Object>> rows = List.of(
            Map.of("value", 50L),
            Map.of("value", 150L),  // 违规
            Map.of("value", -10L),  // 违规
            Map.of("value", 99L)
        );

        ViolationResult result = engine.execute(rule, rows, ExecutionHints.auto());

        assertThat(result.totalRows()).isEqualTo(4);
        assertThat(result.violationCount()).isEqualTo(2);
        assertThat(result.samples()).hasSize(2);
        assertThat(result.backendUsed()).isEqualTo(Backend.JVM);
        assertThat(result.hasViolations()).isTrue();
        assertThat(result.violationRate()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("批量执行也会记录 invocation（EXECUTE 模式）")
    void executeRecordsInvocation() {
        CompiledRule rule = engine.compile("x > 0");
        List<Map<String, Object>> rows = List.of(Map.of("x", 1L), Map.of("x", 2L));

        engine.execute(rule, rows, ExecutionHints.auto());

        List<InvocationRecord> recent = engine.recorder().recent("src-junit-test", 10);
        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).mode()).isEqualTo(InvocationRecord.Mode.EXECUTE);
        assertThat(recent.get(0).result()).contains("violations");
    }

    @Test
    @DisplayName("非法 CEL 表达式抛 CompilationException")
    void invalidCelThrowsCompilationException() {
        assertThatThrownBy(() -> engine.compile("this is not valid CEL @#$%"))
            .isInstanceOf(CompilationException.class);
    }

    @Test
    @DisplayName("空表达式抛 CompilationException")
    void emptyExpressionThrows() {
        assertThatThrownBy(() -> engine.compile(""))
            .isInstanceOf(CompilationException.class);
        assertThatThrownBy(() -> engine.compile(null))
            .isInstanceOf(CompilationException.class);
    }

    @Test
    @DisplayName("countByCaller——按来源统计调用次数")
    void countByCaller() {
        CompiledRule rule = engine.compile("x > 0");
        engine.eval(rule, Map.of("x", 1L), InvocationContext.of("src-a"));
        engine.eval(rule, Map.of("x", 2L), InvocationContext.of("src-a"));
        engine.eval(rule, Map.of("x", 3L), InvocationContext.of("src-b"));

        assertThat(engine.recorder().countByCaller("src-a")).isEqualTo(2);
        assertThat(engine.recorder().countByCaller("src-b")).isEqualTo(1);
        assertThat(engine.recorder().countByCaller("src-x")).isEqualTo(0);
    }
}
