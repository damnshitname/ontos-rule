package com.ontos.rule.core.model;

import java.util.UUID;

/**
 * 调用上下文。每次 core API 调用必带，用于追溯到具体来源。
 *
 * <p>典型用法：
 * <pre>{@code
 * engine.eval(rule, data, InvocationContext.of("src-ontos-business"));
 * }</pre>
 *
 * @param caller   来源系统标识，例如 "src-ontos-business" / "src-tenant-skytech"
 * @param user     调用方用户/服务标识（可选）
 * @param ruleId   业务规则 ID（可选，便于关联业务层规则）
 * @param traceId  分布式 trace ID（用于跨系统链路追踪）
 */
public record InvocationContext(
    String caller,
    String user,
    String ruleId,
    String traceId
) {
    public static InvocationContext of(String caller) {
        return new InvocationContext(caller, "system", null, UUID.randomUUID().toString());
    }

    public static InvocationContext of(String caller, String user) {
        return new InvocationContext(caller, user, null, UUID.randomUUID().toString());
    }

    public static InvocationContext anonymous() {
        return new InvocationContext("anonymous", "anonymous", null, UUID.randomUUID().toString());
    }
}
