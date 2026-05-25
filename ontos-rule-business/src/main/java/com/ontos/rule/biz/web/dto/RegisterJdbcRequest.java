package com.ontos.rule.biz.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 注册 JDBC 数据源请求。
 *
 * <pre>
 * POST /api/data-sources/jdbc
 * {
 *   "name": "mysql-test",
 *   "jdbcUrl": "jdbc:mysql://localhost:3306/test",
 *   "username": "root",
 *   "password": "yourpwd",
 *   "tableName": "lot_main",
 *   "dialect": "mysql"
 * }
 * </pre>
 *
 * <p>dialect 支持: postgresql, mysql, oracle, hive, impala, starrocks, kudu, h2
 */
public record RegisterJdbcRequest(
    @NotBlank String name,
    @NotBlank String jdbcUrl,
    @NotBlank String username,
    String password,
    @NotBlank String tableName,
    @NotBlank String dialect
) {}
