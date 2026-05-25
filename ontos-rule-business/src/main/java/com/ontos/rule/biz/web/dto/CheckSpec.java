package com.ontos.rule.biz.web.dto;

import java.util.Map;

/**
 * 单个维度卡片的规格。
 *
 * <p>表单形态规则由一组 CheckSpec 组成，由 RuleTypeRegistry 编译成 CEL。
 *
 * <pre>{@code
 * {"type": "range", "params": {"min": 0, "max": 100}}
 * → "value >= 0 && value <= 100"
 *
 * {"type": "enum", "params": {"allowed": ["RUNNING", "IDLE"]}}
 * → "value in [\"RUNNING\", \"IDLE\"]"
 * }</pre>
 */
public record CheckSpec(String type, Map<String, Object> params) {
    public CheckSpec {
        if (params == null) params = Map.of();
    }
}
