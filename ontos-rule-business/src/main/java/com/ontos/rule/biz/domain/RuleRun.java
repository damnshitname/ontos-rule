package com.ontos.rule.biz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 规则执行历史。一次 execute() 调用产生一条记录。
 */
@Entity
@Table(name = "rule_run", indexes = {
    @Index(name = "idx_rule_run_rule_id", columnList = "rule_id"),
    @Index(name = "idx_rule_run_started", columnList = "started_at")
})
public class RuleRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false, length = 64)
    private String ruleId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private long durationMs;

    @Column(name = "total_rows")
    private long totalRows;

    @Column(name = "violation_count")
    private long violationCount;

    @Column(name = "backend_used", length = 16)
    private String backendUsed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RunStatus status;

    @Column(name = "data_source", length = 64)
    private String dataSource;

    @Column(name = "caller", length = 64)
    private String caller;

    @Column(columnDefinition = "TEXT")
    private String error;

    public RuleRun() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public long getTotalRows() { return totalRows; }
    public void setTotalRows(long totalRows) { this.totalRows = totalRows; }
    public long getViolationCount() { return violationCount; }
    public void setViolationCount(long violationCount) { this.violationCount = violationCount; }
    public String getBackendUsed() { return backendUsed; }
    public void setBackendUsed(String backendUsed) { this.backendUsed = backendUsed; }
    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) { this.status = status; }
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    public String getCaller() { return caller; }
    public void setCaller(String caller) { this.caller = caller; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
