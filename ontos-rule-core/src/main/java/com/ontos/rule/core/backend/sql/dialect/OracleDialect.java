package com.ontos.rule.core.backend.sql.dialect;

/**
 * Oracle 方言。Phase 1 stub。
 *
 * <p>主要差异：
 * <ul>
 *   <li>没有 LIMIT，要用 ROWNUM 或 FETCH FIRST N ROWS ONLY (12c+)</li>
 *   <li>字符串拼接: ||（同 PG）</li>
 *   <li>SUM(CASE WHEN ...) 兼容</li>
 * </ul>
 */
public class OracleDialect implements SqlDialect {

    @Override
    public String name() {
        return "oracle";
    }

    @Override
    public String quoteIdentifier(String id) {
        return "\"" + id.toUpperCase().replace("\"", "\"\"") + "\"";
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
        return "REGEXP_LIKE(" + str + ", " + regex + ")";
    }

    @Override
    public String startsWithFunction(String str, String prefix) {
        return "(" + str + " LIKE " + prefix + " || '%')";
    }

    @Override
    public String endsWithFunction(String str, String suffix) {
        return "(" + str + " LIKE '%' || " + suffix + ")";
    }

    @Override
    public String containsFunction(String str, String substring) {
        return "(INSTR(" + str + ", " + substring + ") > 0)";
    }

    @Override
    public String buildSampleQuery(String tableName, String whereClause, int limit) {
        // Oracle 12c+: FETCH FIRST n ROWS ONLY
        return "SELECT * FROM " + quoteIdentifier(tableName) +
            " WHERE NOT (" + whereClause + ") FETCH FIRST " + limit + " ROWS ONLY";
    }
}
