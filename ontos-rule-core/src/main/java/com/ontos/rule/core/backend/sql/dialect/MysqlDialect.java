package com.ontos.rule.core.backend.sql.dialect;

/**
 * MySQL 方言。Phase 1 stub - 基础语法继承自 PostgreSQL，仅覆盖差异点。
 *
 * <p>主要差异：
 * <ul>
 *   <li>标识符引号: 反引号 `</li>
 *   <li>正则: REGEXP 而非 ~</li>
 *   <li>字符串拼接: CONCAT() 而非 ||</li>
 * </ul>
 */
public class MysqlDialect implements SqlDialect {

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public String quoteIdentifier(String id) {
        return "`" + id.replace("`", "``") + "`";
    }

    @Override
    public String quoteString(String value) {
        return "'" + value.replace("'", "''").replace("\\", "\\\\") + "'";
    }

    @Override
    public String lengthFunction(String arg) {
        return "CHAR_LENGTH(" + arg + ")";
    }

    @Override
    public String matchesFunction(String str, String regex) {
        return "(" + str + " REGEXP " + regex + ")";
    }

    @Override
    public String startsWithFunction(String str, String prefix) {
        return "(" + str + " LIKE CONCAT(" + prefix + ", '%'))";
    }

    @Override
    public String endsWithFunction(String str, String suffix) {
        return "(" + str + " LIKE CONCAT('%', " + suffix + "))";
    }

    @Override
    public String containsFunction(String str, String substring) {
        return "(INSTR(" + str + ", " + substring + ") > 0)";
    }

    @Override
    public boolean isImplemented() {
        // Phase 1 标记为未完整测试；可用但需要回归
        return true;
    }
}
