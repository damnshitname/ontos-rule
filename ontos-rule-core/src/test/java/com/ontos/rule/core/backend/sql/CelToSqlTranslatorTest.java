package com.ontos.rule.core.backend.sql;

import com.ontos.rule.core.backend.sql.dialect.MysqlDialect;
import com.ontos.rule.core.backend.sql.dialect.PostgresqlDialect;
import com.ontos.rule.core.backend.sql.translator.CelToSqlTranslator;
import com.ontos.rule.core.backend.sql.translator.TranslationException;
import com.ontos.rule.core.compiler.CelCompiler;
import com.ontos.rule.core.model.CompiledRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CEL → SQL 翻译器单元测试。
 *
 * <p>不连数据库，纯文本断言。
 */
class CelToSqlTranslatorTest {

    private final CelCompiler compiler = new CelCompiler();
    private final CelToSqlTranslator pg = new CelToSqlTranslator(new PostgresqlDialect());
    private final CelToSqlTranslator mysql = new CelToSqlTranslator(new MysqlDialect());

    private String pgSql(String cel) {
        CompiledRule rule = compiler.compile(cel);
        return pg.translate(rule.ast());
    }

    @Test
    @DisplayName("数字比较：value > 100")
    void simpleComparison() {
        assertThat(pgSql("value > 100")).isEqualTo("(\"value\" > 100)");
    }

    @Test
    @DisplayName("算术：a + b < c (你之前担心的场景)")
    void arithmetic() {
        assertThat(pgSql("a + b < c"))
            .isEqualTo("((\"a\" + \"b\") < \"c\")");
    }

    @Test
    @DisplayName("逻辑组合 + 字符串字面量")
    void logicalAndStringLiteral() {
        String sql = pgSql("status == \"RUNNING\" && temperature < 800");
        assertThat(sql).isEqualTo("((\"status\" = 'RUNNING') AND (\"temperature\" < 800))");
    }

    @Test
    @DisplayName("in 列表")
    void inList() {
        String sql = pgSql("status in [\"A\", \"B\", \"C\"]");
        assertThat(sql).isEqualTo("(\"status\" IN ('A', 'B', 'C'))");
    }

    @Test
    @DisplayName("size() 函数")
    void sizeFunction() {
        String sql = pgSql("size(name) >= 5");
        assertThat(sql).isEqualTo("(LENGTH(\"name\") >= 5)");
    }

    @Test
    @DisplayName("逻辑非")
    void logicalNot() {
        String sql = pgSql("!(value > 100)");
        assertThat(sql).isEqualTo("(NOT (\"value\" > 100))");
    }

    @Test
    @DisplayName("三目运算符")
    void ternary() {
        String sql = pgSql("vip ? amount > 1000 : amount > 100");
        assertThat(sql).contains("CASE WHEN").contains("THEN").contains("ELSE");
    }

    @Test
    @DisplayName("方言差异 - size() 在 MySQL 是 CHAR_LENGTH")
    void dialectDifference_mysql() {
        CompiledRule rule = compiler.compile("size(name) > 5");
        String pgSql = pg.translate(rule.ast());
        String mysqlSql = mysql.translate(rule.ast());

        assertThat(pgSql).contains("LENGTH(");
        assertThat(mysqlSql).contains("CHAR_LENGTH(");
        // 标识符引号也不同
        assertThat(pgSql).contains("\"name\"");
        assertThat(mysqlSql).contains("`name`");
    }

    @Test
    @DisplayName("不支持的特性会抛异常 (编译或翻译阶段)")
    void unsupportedThrows() {
        // 这类高级 CEL 特性要么编译期被拒，要么翻译期抛 TranslationException
        // 都算正确——SQL Backend 明确告诉用户该走 JVM Backend
        assertThatThrownBy(() -> pgSql("[1, 2, 3].all(x, x > 0)"))
            .isInstanceOf(RuntimeException.class);
    }
}
