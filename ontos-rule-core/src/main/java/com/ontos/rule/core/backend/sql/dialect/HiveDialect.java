package com.ontos.rule.core.backend.sql.dialect;

/**
 * Hive 方言。Phase 1 stub。
 *
 * <p>主要差异：
 * <ul>
 *   <li>反引号引号</li>
 *   <li>RLIKE 而非 REGEXP</li>
 *   <li>字符串拼接: CONCAT()</li>
 *   <li>支持 LIMIT</li>
 * </ul>
 */
public class HiveDialect implements SqlDialect {

    @Override
    public String name() {
        return "hive";
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
        return "(" + str + " RLIKE " + regex + ")";
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
