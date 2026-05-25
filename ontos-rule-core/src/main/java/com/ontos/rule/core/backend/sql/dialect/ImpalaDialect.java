package com.ontos.rule.core.backend.sql.dialect;

/**
 * Impala 方言。Phase 1 stub。
 *
 * <p>兼容 Hive 语法，主要差异：
 * <ul>
 *   <li>timestamp 处理略不同</li>
 *   <li>Kudu 表通过 Impala 访问</li>
 * </ul>
 */
public class ImpalaDialect implements SqlDialect {

    @Override
    public String name() {
        return "impala";
    }

    @Override
    public String quoteIdentifier(String id) {
        return "`" + id.replace("`", "``") + "`";
    }

    @Override
    public String quoteString(String value) {
        return "'" + value.replace("'", "\\'") + "'";
    }

    @Override
    public String lengthFunction(String arg) {
        return "length(" + arg + ")";
    }

    @Override
    public String matchesFunction(String str, String regex) {
        return "regexp_like(" + str + ", " + regex + ")";
    }

    @Override
    public String startsWithFunction(String str, String prefix) {
        return "(" + str + " LIKE concat(" + prefix + ", '%'))";
    }

    @Override
    public String endsWithFunction(String str, String suffix) {
        return "(" + str + " LIKE concat('%', " + suffix + "))";
    }

    @Override
    public String containsFunction(String str, String substring) {
        return "(instr(" + str + ", " + substring + ") > 0)";
    }
}
