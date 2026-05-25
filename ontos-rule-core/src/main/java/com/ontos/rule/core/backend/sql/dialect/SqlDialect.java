package com.ontos.rule.core.backend.sql.dialect;

/**
 * SQL 方言接口。
 *
 * <p>不同数据库 SQL 语法有细微差异（函数名、引号、字符串拼接等），
 * CelToSqlTranslator 通过此接口调用方言相关 API，
 * 让 AST 翻译逻辑跟方言解耦。
 *
 * <p>已实现方言：
 * <ul>
 *   <li>PostgreSQL (P0)</li>
 *   <li>MySQL (P0 stub)</li>
 *   <li>Oracle (P0 stub)</li>
 *   <li>Hive (P1 stub)</li>
 *   <li>StarRocks (P1 stub, MySQL 兼容)</li>
 *   <li>Impala (P1 stub)</li>
 * </ul>
 */
public interface SqlDialect {

    /** 方言名，如 "postgresql" / "mysql" / "hive" */
    String name();

    /** 标识符引号，如 PG 用 ", MySQL 用 `, Oracle 用 " */
    String quoteIdentifier(String identifier);

    /** 字符串字面量引号，e.g. 'abc' → 'abc'（内部单引号转义） */
    String quoteString(String value);

    /** size(s) 函数 */
    String lengthFunction(String arg);

    /** 字符串正则匹配 matches(s, regex) */
    String matchesFunction(String str, String regex);

    /** startsWith(s, prefix) */
    String startsWithFunction(String str, String prefix);

    /** endsWith(s, suffix) */
    String endsWithFunction(String str, String suffix);

    /** contains(s, substr) */
    String containsFunction(String str, String substring);

    /**
     * 构造"统计总数 + 违规数"的 SQL。
     * 翻译时已经把 CEL 取反，所以 violations = COUNT(*) WHERE NOT (whereClause)。
     */
    default String buildCountQuery(String tableName, String whereClause) {
        return "SELECT COUNT(*) AS total, " +
            "SUM(CASE WHEN NOT (" + whereClause + ") THEN 1 ELSE 0 END) AS violations " +
            "FROM " + quoteIdentifier(tableName);
    }

    /**
     * 构造"违规样本"的 SQL。
     */
    default String buildSampleQuery(String tableName, String whereClause, int limit) {
        return "SELECT * FROM " + quoteIdentifier(tableName) +
            " WHERE NOT (" + whereClause + ") LIMIT " + limit;
    }

    /** 是否支持当前实现 */
    default boolean isImplemented() {
        return true;
    }
}
