package com.ontos.rule.biz.web.dto;

import com.ontos.rule.biz.domain.QualityScore;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 评分快照 DTO。dimensions map 把 6 维分一起返回，方便前端画雷达图。
 */
public record ScoreDto(
    String targetType,
    String targetId,
    Instant snapshotAt,
    BigDecimal overallScore,
    String grade,
    Map<String, BigDecimal> dimensions,
    Integer rulesCount,
    Long violationsCount,
    Long totalRows,
    String triggerType
) {
    public static ScoreDto from(QualityScore q) {
        if (q == null) return null;
        Map<String, BigDecimal> dims = new LinkedHashMap<>();
        dims.put("completeness", q.getCompleteness());
        dims.put("uniqueness", q.getUniqueness());
        dims.put("validity", q.getValidity());
        dims.put("consistency", q.getConsistency());
        dims.put("accuracy", q.getAccuracy());
        dims.put("timeliness", q.getTimeliness());
        return new ScoreDto(
            q.getTargetType(), q.getTargetId(), q.getSnapshotAt(),
            q.getOverallScore(), q.getGrade(), dims,
            q.getRulesCount(), q.getViolationsCount(), q.getTotalRows(),
            q.getTriggerType()
        );
    }
}
