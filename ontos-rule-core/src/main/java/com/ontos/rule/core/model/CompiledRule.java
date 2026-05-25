package com.ontos.rule.core.model;

import dev.cel.common.CelAbstractSyntaxTree;

import java.time.Instant;
import java.util.Set;

/**
 * 编译后的规则。
 *
 * <p>由 CelCompiler 产生，缓存复用以避免重复解析。
 * AST 是 dev.cel 的原生类型，后续 Backend 直接消费。
 *
 * @param expression   原始 CEL 表达式字符串
 * @param ast          CEL 编译产物（dev.cel.common.CelAbstractSyntaxTree）
 * @param variables    表达式引用的变量名集合
 * @param compiledAt   编译时间
 */
public record CompiledRule(
    String expression,
    CelAbstractSyntaxTree ast,
    Set<String> variables,
    Instant compiledAt
) {}
