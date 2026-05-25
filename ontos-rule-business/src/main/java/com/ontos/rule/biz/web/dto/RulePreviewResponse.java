package com.ontos.rule.biz.web.dto;

import java.util.List;

/**
 * 规则编辑器实时预览响应。
 *
 * <p>失败时 {@code valid=false} + 填 {@code errorMessage}，**不抛异常**——
 * 避免编辑器每次按键都弹 toast。
 */
public record RulePreviewResponse(
    /** 编译后的 CEL 字符串（表单 → 编译产物；CEL 模式 → 原样回显） */
    String compiledExpression,
    /** 检测到的变量名（已过滤 _now 等内部变量） */
    List<String> variables,
    /** 推断维度（表单模式自动；CEL 模式取用户填的 dimensions） */
    List<String> inferredDimensions,
    /** 语法是否合法 */
    boolean valid,
    /** 不合法时的具体错误（含位置） */
    String errorMessage
) {}
