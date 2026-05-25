package com.ontos.rule.biz.web.dto;

import com.ontos.rule.core.model.InvocationRecord;

import java.time.Instant;

public record InvocationDto(
    String id,
    String caller,
    String user,
    String ruleId,
    String traceId,
    String expression,
    String input,
    String mode,
    String result,
    String backend,
    long latencyMs,
    Instant timestamp,
    String error
) {
    public static InvocationDto from(InvocationRecord r) {
        return new InvocationDto(
            r.id(),
            r.context().caller(),
            r.context().user(),
            r.context().ruleId(),
            r.context().traceId(),
            r.expression(),
            r.input(),
            r.mode().name(),
            r.result(),
            r.backend().name(),
            r.latency().toMillis(),
            r.timestamp(),
            r.error()
        );
    }
}
