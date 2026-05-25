package com.ontos.rule.biz.web.dto;

public record EvalResponse(
    boolean result,
    long latencyMs,
    String backend,
    String caller
) {}
