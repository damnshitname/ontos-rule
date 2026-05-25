package com.ontos.rule.biz.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 创建规则请求 - 支持双形态。
 *
 * <h3>形态 A：表单（维度卡片）</h3>
 * <pre>{@code
 * {
 *   "name": "Lot.lotId 必填且唯一",
 *   "target": "Lot.lotId",
 *   "severity": "ERROR",
 *   "backendHint": "AUTO",
 *   "formChecks": [
 *     {"type": "not_blank"},
 *     {"type": "unique"},
 *     {"type": "pattern", "params": {"regex": "^[A-Z]{2}\\d{8}$"}}
 *   ]
 * }
 * }</pre>
 *
 * <h3>形态 B：CEL 表达式</h3>
 * <pre>{@code
 * {
 *   "name": "温度 + 容差 < 上限",
 *   "target": "Lot",
 *   "expression": "temperature + tolerance < maxLimit",
 *   "severity": "WARN",
 *   "backendHint": "AUTO",
 *   "dimensions": "validity,consistency"
 * }
 * }</pre>
 *
 * <p>两个互斥：要么传 formChecks，要么传 expression。
 */
public record CreateRuleRequest(
    String id,
    @NotBlank String name,
    @NotBlank String target,

    /** 表单形态：维度卡片列表。null 表示走 CEL 形态 */
    List<CheckSpec> formChecks,

    /** CEL 形态：自由表达式。null 表示走表单形态 */
    String expression,

    /** 评分维度（逗号分隔），CEL 形态需手动指定；表单形态自动推断 */
    String dimensions,

    @NotBlank String severity,
    @NotBlank String backendHint,
    String owner
) {}
