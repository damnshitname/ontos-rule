package com.ontos.rule.core.compiler;

import com.ontos.rule.core.model.CompiledRule;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelValidationResult;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompilerBuilder;
import dev.cel.compiler.CelCompilerFactory;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CEL 表达式编译器。
 *
 * <p>把 CEL 字符串编译成 AST，供 Backend 求值。
 * 编译产物可缓存复用，避免每次重复解析。
 *
 * <p>变量声明策略：所有引用变量按 DYN 类型声明（运行时检查），
 * 便于"无 schema"场景使用。如需严格类型检查，调用方可显式传入 declaredVars。
 */
public class CelCompiler {

    /** CEL 关键字/内建函数，提取变量时跳过 */
    private static final Set<String> RESERVED = Set.of(
        "true", "false", "null", "in",
        "size", "matches", "contains", "startsWith", "endsWith",
        "now", "duration", "timestamp",
        "has", "exists", "exists_one", "all", "filter", "map",
        "int", "uint", "double", "string", "bytes", "bool",
        "type", "dyn"
    );

    private static final Pattern IDENT_PATTERN =
        Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b");

    /**
     * 编译表达式，自动从表达式中提取变量名。
     */
    public CompiledRule compile(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new CompilationException("表达式不能为空");
        }
        Set<String> vars = extractVars(expression);
        return compile(expression, vars);
    }

    /**
     * 编译表达式，使用显式声明的变量列表（更严格的类型检查）。
     */
    public CompiledRule compile(String expression, Set<String> declaredVars) {
        if (expression == null || expression.isBlank()) {
            throw new CompilationException("表达式不能为空");
        }
        try {
            CelCompilerBuilder builder = CelCompilerFactory.standardCelCompilerBuilder();
            for (String v : declaredVars) {
                // _now 是 time_within handler 注入的"当前时间"特殊变量，必须显式声明为 TIMESTAMP
                // 否则 (_now - timestamp(value)) 在编译期类型推断失败
                if ("_now".equals(v)) {
                    builder.addVar(v, SimpleType.TIMESTAMP);
                } else {
                    builder.addVar(v, SimpleType.DYN);
                }
            }
            dev.cel.compiler.CelCompiler compiler = builder.build();
            CelValidationResult result = compiler.compile(expression);
            if (result.hasError()) {
                throw new CompilationException(
                    "CEL 编译失败: " + result.getErrorString() + " · 表达式: " + expression
                );
            }
            CelAbstractSyntaxTree ast = result.getAst();
            return new CompiledRule(expression, ast, declaredVars, Instant.now());
        } catch (CelValidationException e) {
            throw new CompilationException(
                "CEL 编译异常: " + e.getMessage() + " · 表达式: " + expression, e
            );
        }
    }

    /**
     * 从表达式中提取变量名（简单基于正则的标识符提取）。
     * 跳过 CEL 关键字和内建函数。
     */
    Set<String> extractVars(String expression) {
        Set<String> vars = new LinkedHashSet<>();
        Matcher m = IDENT_PATTERN.matcher(expression);
        while (m.find()) {
            String token = m.group(1);
            if (!RESERVED.contains(token) && !Character.isDigit(token.charAt(0))) {
                vars.add(token);
            }
        }
        return vars;
    }
}
