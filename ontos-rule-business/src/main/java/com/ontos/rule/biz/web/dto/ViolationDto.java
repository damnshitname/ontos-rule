package com.ontos.rule.biz.web.dto;

import com.ontos.rule.biz.domain.Violation;

import java.time.Instant;

public record ViolationDto(
    Long id,
    Long runId,
    String targetPk,
    String violatingValue,
    String context,
    Instant sampledAt
) {
    public static ViolationDto from(Violation v) {
        return new ViolationDto(
            v.getId(), v.getRunId(),
            v.getTargetPk(), v.getViolatingValue(),
            v.getContext(), v.getSampledAt()
        );
    }
}
