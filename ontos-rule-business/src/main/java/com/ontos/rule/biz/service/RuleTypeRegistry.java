package com.ontos.rule.biz.service;

import com.ontos.rule.biz.web.dto.CheckSpec;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 维度卡片注册中心 + 10 个内置 Handler 实现。
 *
 * <p>把表单形态的 CheckSpec[] 编译成单个 CEL 字符串，
 * 然后交给 core 引擎执行（core 不知道有"维度"这个概念）。
 *
 * <p>未来扩展维度只需在此加 inner class + register(...)。
 */
@Service
public class RuleTypeRegistry {

    private final Map<String, RuleTypeHandler> handlers = new LinkedHashMap<>();

    public RuleTypeRegistry() {
        register(new NotNullHandler());
        register(new NotBlankHandler());
        register(new UniqueHandler());
        register(new PatternHandler());
        register(new RangeHandler());
        register(new LengthHandler());
        register(new EnumHandler());
        register(new StartsWithHandler());
        register(new TimeWithinHandler());
        register(new CrossFieldHandler());
    }

    private void register(RuleTypeHandler h) {
        handlers.put(h.type(), h);
    }

    public RuleTypeHandler get(String type) {
        RuleTypeHandler h = handlers.get(type);
        if (h == null) {
            throw new IllegalArgumentException(
                "未知维度 type: " + type + " · 支持: " + handlers.keySet()
            );
        }
        return h;
    }

    public List<RuleTypeHandler> listAll() {
        return List.copyOf(handlers.values());
    }

    /**
     * 把多个 CheckSpec 编译为单个 CEL 表达式（用 && 连接）。
     *
     * <p>handler 写出来的 CEL 用 {@code value} 作为待校验列的占位符，
     * 这里根据 target 把 {@code value} 替换为真实列名（如 {@code lotId}），
     * 这样执行时 row map 的字段才能正确绑定。
     *
     * @param checks   表单维度卡片列表
     * @param target   规则的 target，形如 {@code Lot.lotId} / {@code Lot}
     */
    public String compileChecks(List<CheckSpec> checks, String target) {
        if (checks == null || checks.isEmpty()) {
            throw new IllegalArgumentException("checks 不能为空");
        }
        String joined = checks.stream()
            .map(this::compileOne)
            .map(s -> "(" + s + ")")
            .collect(Collectors.joining(" && "));
        String column = extractColumn(target);
        if (column == null) return joined;
        // 整词替换 value → 真实列名，避免误伤 valueXxx 之类的标识
        return joined.replaceAll("\\bvalue\\b", java.util.regex.Matcher.quoteReplacement(column));
    }

    /** target {@code Lot.lotId} → {@code lotId}; {@code Lot} → {@code null}（无列时不替换） */
    private String extractColumn(String target) {
        if (target == null) return null;
        int dot = target.indexOf('.');
        if (dot < 0 || dot == target.length() - 1) return null;
        return target.substring(dot + 1).trim();
    }

    /**
     * 从 CheckSpec 列表推断该规则所属的评分维度集合。
     */
    public Set<String> inferDimensions(List<CheckSpec> checks) {
        if (checks == null || checks.isEmpty()) return Set.of();
        return checks.stream()
            .map(c -> get(c.type()).dimension())
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private String compileOne(CheckSpec spec) {
        RuleTypeHandler h = get(spec.type());
        h.validate(spec.params());
        return h.compile(spec.params());
    }

    // ============================================================
    // 10 个内置 Handler 实现
    // ============================================================

    static class NotNullHandler implements RuleTypeHandler {
        public String type() { return "not_null"; }
        public String displayName() { return "空值校验"; }
        public String dimension() { return "completeness"; }
        public String compile(Map<String, Object> p) { return "value != null"; }
    }

    static class NotBlankHandler implements RuleTypeHandler {
        public String type() { return "not_blank"; }
        public String displayName() { return "非空字符串"; }
        public String dimension() { return "completeness"; }
        public String compile(Map<String, Object> p) {
            return "value != null && size(value) > 0";
        }
    }

    /** 唯一性：SQL Backend 用 GROUP BY 实现，CEL 中无法表达单行——返回 true，由调度层处理 */
    static class UniqueHandler implements RuleTypeHandler {
        public String type() { return "unique"; }
        public String displayName() { return "唯一性"; }
        public String dimension() { return "uniqueness"; }
        public String compile(Map<String, Object> p) {
            // 占位：实际执行时由 ExecutionService 识别 type=unique，走特殊 SQL 分支
            return "value != null";
        }
    }

    static class PatternHandler implements RuleTypeHandler {
        public String type() { return "pattern"; }
        public String displayName() { return "正则校验"; }
        public String dimension() { return "validity"; }
        public void validate(Map<String, Object> p) {
            if (p.get("regex") == null) throw new IllegalArgumentException("pattern 需要 regex 参数");
        }
        public String compile(Map<String, Object> p) {
            // 字符串字面量需要双引号包裹，转义内部双引号
            String regex = p.get("regex").toString().replace("\\", "\\\\").replace("\"", "\\\"");
            return "matches(value, \"" + regex + "\")";
        }
    }

    static class RangeHandler implements RuleTypeHandler {
        public String type() { return "range"; }
        public String displayName() { return "数值范围"; }
        public String dimension() { return "validity"; }
        public void validate(Map<String, Object> p) {
            if (p.get("min") == null && p.get("max") == null)
                throw new IllegalArgumentException("range 至少需要 min 或 max 之一");
        }
        public String compile(Map<String, Object> p) {
            Object min = p.get("min"), max = p.get("max");
            StringBuilder sb = new StringBuilder();
            if (min != null) sb.append("value >= ").append(min);
            if (min != null && max != null) sb.append(" && ");
            if (max != null) sb.append("value <= ").append(max);
            return sb.toString();
        }
    }

    static class LengthHandler implements RuleTypeHandler {
        public String type() { return "length"; }
        public String displayName() { return "长度范围"; }
        public String dimension() { return "validity"; }
        public void validate(Map<String, Object> p) {
            if (p.get("min") == null && p.get("max") == null)
                throw new IllegalArgumentException("length 至少需要 min 或 max 之一");
        }
        public String compile(Map<String, Object> p) {
            Object min = p.get("min"), max = p.get("max");
            StringBuilder sb = new StringBuilder();
            if (min != null) sb.append("size(value) >= ").append(min);
            if (min != null && max != null) sb.append(" && ");
            if (max != null) sb.append("size(value) <= ").append(max);
            return sb.toString();
        }
    }

    static class EnumHandler implements RuleTypeHandler {
        public String type() { return "enum"; }
        public String displayName() { return "枚举值"; }
        public String dimension() { return "validity"; }
        public void validate(Map<String, Object> p) {
            Object allowed = p.get("allowed");
            if (allowed == null) throw new IllegalArgumentException("enum 需要 allowed 参数（数组或逗号分隔字符串）");
        }
        public String compile(Map<String, Object> p) {
            Object allowed = p.get("allowed");
            List<String> values;
            if (allowed instanceof List<?> list) {
                values = list.stream().map(Object::toString).toList();
            } else {
                values = java.util.Arrays.stream(allowed.toString().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
            }
            String joined = values.stream()
                .map(v -> "\"" + v.replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(", "));
            return "value in [" + joined + "]";
        }
    }

    static class StartsWithHandler implements RuleTypeHandler {
        public String type() { return "starts_with"; }
        public String displayName() { return "字符串前缀"; }
        public String dimension() { return "validity"; }
        public void validate(Map<String, Object> p) {
            if (p.get("prefix") == null) throw new IllegalArgumentException("starts_with 需要 prefix 参数");
        }
        public String compile(Map<String, Object> p) {
            // cel-java 不支持自由函数 startsWith(s, p)，只支持成员调用 string.startsWith(p)
            // 显式 string(value) 转换，让 dyn 类型 dispatch 到 string overload
            String prefix = p.get("prefix").toString().replace("\"", "\\\"");
            return "string(value).startsWith(\"" + prefix + "\")";
        }
    }

    static class TimeWithinHandler implements RuleTypeHandler {
        public String type() { return "time_within"; }
        public String displayName() { return "时间窗口"; }
        public String dimension() { return "timeliness"; }
        public void validate(Map<String, Object> p) {
            if (p.get("days") == null) throw new IllegalArgumentException("time_within 需要 days 参数");
        }
        public String compile(Map<String, Object> p) {
            // cel-java 未注册 now() 函数。CelCompiler 把 _now 声明为 TIMESTAMP 类型，
            // JvmBackend 在 eval 时注入 _now = Instant.now()。
            // 用小时数换算（24*N 小时），避免依赖 "Nd" 字面量在 duration() 中的解析差异。
            long hours;
            try { hours = Long.parseLong(p.get("days").toString()) * 24L; }
            catch (NumberFormatException e) { throw new IllegalArgumentException("time_within days 必须是整数: " + p.get("days")); }
            return "_now - timestamp(value) < duration(\"" + hours + "h\")";
        }
    }

    static class CrossFieldHandler implements RuleTypeHandler {
        public String type() { return "cross_field"; }
        public String displayName() { return "跨字段比较"; }
        public String dimension() { return "consistency"; }
        public void validate(Map<String, Object> p) {
            if (p.get("left") == null || p.get("op") == null || p.get("right") == null)
                throw new IllegalArgumentException("cross_field 需要 left, op, right 三个参数");
        }
        public String compile(Map<String, Object> p) {
            Set<String> ops = Set.of("==", "!=", "<", "<=", ">", ">=");
            String op = p.get("op").toString();
            if (!ops.contains(op)) {
                throw new IllegalArgumentException("cross_field op 必须是 " + ops + ", 实际: " + op);
            }
            return p.get("left") + " " + op + " " + p.get("right");
        }
    }
}
