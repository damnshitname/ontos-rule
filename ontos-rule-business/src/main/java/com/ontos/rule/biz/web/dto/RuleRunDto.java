package com.ontos.rule.biz.web.dto;

import com.ontos.rule.biz.domain.RuleRun;

import java.time.Instant;

public record RuleRunDto(
    Long id,
    String ruleId,
    Instant startedAt,
    Instant finishedAt,
    long durationMs,
    long totalRows,
    long violationCount,
    String backendUsed,
    String status,
    String dataSource,
    String caller,
    String error
) {
    public static RuleRunDto from(RuleRun r) {
        return new RuleRunDto(
            r.getId(), r.getRuleId(),
            r.getStartedAt(), r.getFinishedAt(), r.getDurationMs(),
            r.getTotalRows(), r.getViolationCount(),
            r.getBackendUsed(), r.getStatus().name(),
            r.getDataSource(), r.getCaller(), r.getError()
        );
    }
}
