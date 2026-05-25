package com.ontos.rule.biz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 违规记录（采样）。
 * 一次 RuleRun 的 sampleLimit 内违规会被写到这里。
 */
@Entity
@Table(name = "violation", indexes = {
    @Index(name = "idx_violation_run_id", columnList = "run_id")
})
public class Violation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    /** 业务主键，e.g. "LOT-2026042901" */
    @Column(name = "target_pk", length = 255)
    private String targetPk;

    /** 违规值（toString），e.g. "825" */
    @Column(name = "violating_value", length = 500)
    private String violatingValue;

    /** 完整上下文 JSON */
    @Column(columnDefinition = "TEXT")
    private String context;

    @Column(name = "sampled_at", nullable = false)
    private Instant sampledAt;

    public Violation() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public String getTargetPk() { return targetPk; }
    public void setTargetPk(String targetPk) { this.targetPk = targetPk; }
    public String getViolatingValue() { return violatingValue; }
    public void setViolatingValue(String violatingValue) { this.violatingValue = violatingValue; }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    public Instant getSampledAt() { return sampledAt; }
    public void setSampledAt(Instant sampledAt) { this.sampledAt = sampledAt; }
}
