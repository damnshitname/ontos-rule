package com.ontos.rule.biz.web.dto;

import com.ontos.rule.biz.domain.ScoreWeight;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 权重配置 DTO。
 *
 * <p>请求/响应示例：
 * <pre>{@code
 * {
 *   "targetId": "Lot",
 *   "weights": {
 *     "completeness": 1.5,
 *     "accuracy": 2.0
 *   }
 * }
 * }</pre>
 */
public record WeightDto(
    String targetId,
    Map<String, BigDecimal> weights
) {
    public static WeightDto from(String targetId, List<ScoreWeight> rows) {
        Map<String, BigDecimal> w = new LinkedHashMap<>();
        for (ScoreWeight r : rows) w.put(r.getDimension(), r.getWeight());
        return new WeightDto(targetId, w);
    }
}
