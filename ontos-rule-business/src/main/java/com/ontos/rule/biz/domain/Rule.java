package com.ontos.rule.biz.domain;

import com.ontos.rule.core.model.Backend;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 业务层规则定义。
 *
 * <p>这是"用户面"的规则模型：含名称、目标、责任人、状态等业务字段。
 * 真正的求值委托给 ontos-rule-core 的 RuleEngine，本类只是"规则的存储壳"。
 */
@Entity
@Table(name = "rule_def")
public class Rule {

    /** 业务 ID, 如 "QR-001"。手工指定或自动生成 */
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 255)
    private String name;

    /** 校验目标，如 "Lot.temperature" 或 "Equipment.status" */
    @Column(nullable = false, length = 255)
    private String target;

    /** CEL 表达式 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String expression;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Backend backendHint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RuleStatus status;

    @Column(length = 64)
    private String owner;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * 表单形态：维度卡片的 JSON 数组（CheckSpec[]）。
     * null 表示 CEL 形态（直接写 expression）。
     */
    @Column(name = "form_checks_json", columnDefinition = "TEXT")
    private String formChecksJson;

    /**
     * 该规则覆盖的评分维度（逗号分隔，如 "completeness,validity"）。
     * 表单形态：由 RuleTypeRegistry 自动推断；CEL 形态：由用户手动指定。
     */
    @Column(name = "dimensions", length = 255)
    private String dimensions;

    public Rule() {}

    public Rule(String id, String name, String target, String expression,
                Severity severity, Backend backendHint, RuleStatus status, String owner) {
        this.id = id;
        this.name = name;
        this.target = target;
        this.expression = expression;
        this.severity = severity;
        this.backendHint = backendHint;
        this.status = status;
        this.owner = owner;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // Getters / Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public Backend getBackendHint() { return backendHint; }
    public void setBackendHint(Backend backendHint) { this.backendHint = backendHint; }
    public RuleStatus getStatus() { return status; }
    public void setStatus(RuleStatus status) { this.status = status; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getFormChecksJson() { return formChecksJson; }
    public void setFormChecksJson(String s) { this.formChecksJson = s; }
    public String getDimensions() { return dimensions; }
    public void setDimensions(String d) { this.dimensions = d; }

    /** 是否表单形态（true=维度卡片 / false=自由 CEL） */
    public boolean isFormMode() {
        return formChecksJson != null && !formChecksJson.isBlank();
    }
}
