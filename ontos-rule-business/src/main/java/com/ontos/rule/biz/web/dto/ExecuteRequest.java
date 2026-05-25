package com.ontos.rule.biz.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 批量执行请求。
 *
 * <p>当前只支持 mock 数据源："mock-lot" | "mock-equipment" | "mock-wafer"。
 * 真实场景换成 JDBC URL / 表名。
 */
public record ExecuteRequest(
    @NotBlank
    String dataSource
) {}
