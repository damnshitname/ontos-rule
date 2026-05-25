package com.ontos.rule.biz.web.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontos.rule.biz.domain.Rule;

import java.time.Instant;
import java.util.List;

public record RuleDto(
    String id,
    String name,
    String target,
    /** 表单形态：CheckSpec 列表；CEL 形态：null */
    List<CheckSpec> formChecks,
    /** 编译后的 CEL（表单形态由 RuleTypeRegistry 编译；CEL 形态原样） */
    String expression,
    /** 规则形态：FORM | CEL，前端据此切 UI */
    String mode,
    /** 评分维度（逗号分隔） */
    String dimensions,
    String severity,
    String backendHint,
    String status,
    String owner,
    Instant createdAt,
    Instant updatedAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static RuleDto from(Rule r) {
        List<CheckSpec> checks = null;
        if (r.getFormChecksJson() != null && !r.getFormChecksJson().isBlank()) {
            try {
                checks = MAPPER.readValue(
                    r.getFormChecksJson(),
                    new TypeReference<List<CheckSpec>>() {}
                );
            } catch (Exception ignored) {
                // 反序列化失败仍返回，模式标 CEL
            }
        }
        return new RuleDto(
            r.getId(), r.getName(), r.getTarget(),
            checks,
            r.getExpression(),
            r.isFormMode() ? "FORM" : "CEL",
            r.getDimensions(),
            r.getSeverity().name(), r.getBackendHint().name(),
            r.getStatus().name(), r.getOwner(),
            r.getCreatedAt(), r.getUpdatedAt()
        );
    }
}
