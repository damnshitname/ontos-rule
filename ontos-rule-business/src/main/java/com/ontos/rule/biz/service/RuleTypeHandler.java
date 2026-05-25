package com.ontos.rule.biz.service;

import java.util.Map;

/**
 * 维度卡片 → CEL 编译器接口。
 *
 * <p>每种维度（not_null / pattern / range 等）对应一个实现。
 * RuleTypeRegistry 持有所有 Handler，调用时按 type 路由。
 *
 * <p>所有 Handler 实现都在 RuleTypeRegistry 内作为静态内部类。
 */
public interface RuleTypeHandler {

    /** 维度 type 标识，如 "not_null" / "pattern" / "range" */
    String type();

    /** 业务话术，给 UI 显示 */
    String displayName();

    /** 归属评分维度（6 维之一）: completeness / uniqueness / validity / consistency / accuracy / timeliness */
    String dimension();

    /** 把参数 + 维度规则编译为 CEL 字符串 */
    String compile(Map<String, Object> params);

    /** 校验参数完整性，参数缺失时抛 IllegalArgumentException */
    default void validate(Map<String, Object> params) {
        // 默认无校验，子类可覆盖
    }
}
