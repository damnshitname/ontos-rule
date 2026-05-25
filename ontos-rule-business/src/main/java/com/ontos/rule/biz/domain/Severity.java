package com.ontos.rule.biz.domain;

/**
 * 规则严重度。
 *
 * <p>ERROR = 违反将阻断业务流程（写入时校验直接拒绝）；
 * WARN = 仅告警，不阻断。
 */
public enum Severity {
    ERROR,
    WARN
}
