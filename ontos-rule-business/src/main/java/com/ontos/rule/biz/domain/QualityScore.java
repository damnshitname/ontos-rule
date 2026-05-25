package com.ontos.rule.biz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 评分快照表 - 每次计算（每日 cron 或手动）产生一条。
 *
 * <p>targetType + targetId 唯一标识打分对象：
 * <ul>
 *   <li>object 级：targetType="object", targetId="Lot" / "Equipment" / ...</li>
 *   <li>project 级：targetType="project", targetId="SkyTech-FAB1"</li>
 * </ul>
 */
@Entity
@Table(name = "quality_score", indexes = {
    @Index(name = "idx_score_target_time", columnList = "target_type,target_id,snapshot_at")
})
public class QualityScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_type", nullable = false, length = 16)
    private String targetType;   // "object" | "project"

    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;

    @Column(name = "snapshot_at", nullable = false)
    private Instant snapshotAt;

    @Column(name = "overall_score", precision = 5, scale = 2)
    private BigDecimal overallScore;

    @Column(length = 2)
    private String grade;        // A+/A/B/C/D

    @Column(precision = 5, scale = 2) private BigDecimal completeness;
    @Column(precision = 5, scale = 2) private BigDecimal uniqueness;
    @Column(precision = 5, scale = 2) private BigDecimal validity;
    @Column(precision = 5, scale = 2) private BigDecimal consistency;
    @Column(precision = 5, scale = 2) private BigDecimal accuracy;
    @Column(precision = 5, scale = 2) private BigDecimal timeliness;

    @Column(name = "rules_count") private Integer rulesCount;
    @Column(name = "violations_count") private Long violationsCount;
    @Column(name = "total_rows") private Long totalRows;

    @Column(name = "trigger_type", length = 16)
    private String triggerType;   // "scheduled" | "manual" | "ci"

    public QualityScore() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String s) { this.targetType = s; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String s) { this.targetId = s; }
    public Instant getSnapshotAt() { return snapshotAt; }
    public void setSnapshotAt(Instant t) { this.snapshotAt = t; }
    public BigDecimal getOverallScore() { return overallScore; }
    public void setOverallScore(BigDecimal s) { this.overallScore = s; }
    public String getGrade() { return grade; }
    public void setGrade(String g) { this.grade = g; }
    public BigDecimal getCompleteness() { return completeness; }
    public void setCompleteness(BigDecimal v) { this.completeness = v; }
    public BigDecimal getUniqueness() { return uniqueness; }
    public void setUniqueness(BigDecimal v) { this.uniqueness = v; }
    public BigDecimal getValidity() { return validity; }
    public void setValidity(BigDecimal v) { this.validity = v; }
    public BigDecimal getConsistency() { return consistency; }
    public void setConsistency(BigDecimal v) { this.consistency = v; }
    public BigDecimal getAccuracy() { return accuracy; }
    public void setAccuracy(BigDecimal v) { this.accuracy = v; }
    public BigDecimal getTimeliness() { return timeliness; }
    public void setTimeliness(BigDecimal v) { this.timeliness = v; }
    public Integer getRulesCount() { return rulesCount; }
    public void setRulesCount(Integer n) { this.rulesCount = n; }
    public Long getViolationsCount() { return violationsCount; }
    public void setViolationsCount(Long n) { this.violationsCount = n; }
    public Long getTotalRows() { return totalRows; }
    public void setTotalRows(Long n) { this.totalRows = n; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String s) { this.triggerType = s; }
}
