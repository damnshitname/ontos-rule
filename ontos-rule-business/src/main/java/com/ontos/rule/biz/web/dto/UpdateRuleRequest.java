package com.ontos.rule.biz.web.dto;

import java.util.List;

/**
 * 部分更新规则请求。null 字段表示不修改。
 *
 * <p>切换形态：传 formChecks 切表单 / 传 expression 切 CEL（互斥）。
 */
public record UpdateRuleRequest(
    String name,
    String target,
    List<CheckSpec> formChecks,
    String expression,
    String dimensions,
    String severity,
    String backendHint,
    /** ACTIVE | DISABLED | DRAFT */
    String status,
    String owner
) {}
