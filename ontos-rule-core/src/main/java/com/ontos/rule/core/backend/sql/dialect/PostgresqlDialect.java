package com.ontos.rule.core.backend.sql.dialect;

/**
 * PostgreSQL 方言。Phase 1 完整实现。
 *
 * <p>H2 在 PostgreSQL MODE 下大部分语法兼容，可用 H2 跑单测。
 */
public class PostgresqlDialect implements SqlDialect {

    @Override
    public String name() {
        return "postgresql";
    }

    @Override
    public String quoteIdentifier(String id) {
        return "\"" + id.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String quoteString(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    @Override
    public String lengthFunction(String arg) {
        return "LENGTH(" + arg + ")";
    }

    @Override
    public String matchesFunction(String str, String regex) {
        // PG 正则操作符 ~
        return "(" + str + " ~ " + regex + ")";
    }

    @Override
    public String startsWithFunction(String str, String prefix) {
        // PG 支持 starts_with()，但用 LIKE 兼容性更好
        return "(" + str + " LIKE " + prefix + " || '%')";
    }

    @Override
    public String endsWithFunction(String str, String suffix) {
        return "(" + str + " LIKE '%' || " + suffix + ")";
    }

    @Override
    public String containsFunction(String str, String substring) {
        return "(POSITION(" + substring + " IN " + str + ") > 0)";
    }
}
