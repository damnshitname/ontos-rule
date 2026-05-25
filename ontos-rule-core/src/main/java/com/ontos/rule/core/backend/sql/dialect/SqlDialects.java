package com.ontos.rule.core.backend.sql.dialect;

import java.util.Locale;

/**
 * 方言工厂。按名字获取 SqlDialect 实例。
 */
public final class SqlDialects {

    private SqlDialects() {}

    public static SqlDialect of(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT).trim();
        return switch (n) {
            case "postgresql", "postgres", "pg" -> new PostgresqlDialect();
            case "mysql" -> new MysqlDialect();
            case "oracle" -> new OracleDialect();
            case "hive" -> new HiveDialect();
            case "impala" -> new ImpalaDialect();
            case "starrocks" -> new StarRocksDialect();
            case "kudu" -> new ImpalaDialect();  // Kudu 通过 Impala
            case "h2" -> new PostgresqlDialect();  // H2 PG mode 兼容
            default -> throw new IllegalArgumentException(
                "未知 SQL 方言: " + name + " · 支持: postgresql, mysql, oracle, hive, impala, starrocks, kudu, h2"
            );
        };
    }
}
