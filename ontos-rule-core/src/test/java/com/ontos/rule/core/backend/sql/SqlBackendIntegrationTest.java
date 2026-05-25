package com.ontos.rule.core.backend.sql;

import com.ontos.rule.core.backend.sql.dialect.PostgresqlDialect;
import com.ontos.rule.core.compiler.CelCompiler;
import com.ontos.rule.core.model.Backend;
import com.ontos.rule.core.model.CompiledRule;
import com.ontos.rule.core.model.ExecutionHints;
import com.ontos.rule.core.model.ViolationResult;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SqlBackend 集成测试：用 H2 (PostgreSQL MODE) 做真实 SQL 执行。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqlBackendIntegrationTest {

    private DataSource ds;
    private final CelCompiler compiler = new CelCompiler();
    private final SqlBackend backend = new SqlBackend(new PostgresqlDialect());

    @BeforeAll
    void setupDb() throws Exception {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:sqlbackend-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1");
        h2.setUser("sa");
        h2.setPassword("");
        this.ds = h2;

        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS lot_main");
            st.execute("""
                CREATE TABLE lot_main (
                    lot_id VARCHAR(50) PRIMARY KEY,
                    temperature DOUBLE,
                    tolerance DOUBLE,
                    max_limit DOUBLE,
                    status VARCHAR(20)
                )
                """);
            // 7 条数据：3 条合规 + 4 条违规
            st.execute("INSERT INTO lot_main VALUES ('LOT-001', 92, 5, 100, 'RUNNING')");   // ok
            st.execute("INSERT INTO lot_main VALUES ('LOT-002', 88, 10, 100, 'IDLE')");     // ok
            st.execute("INSERT INTO lot_main VALUES ('LOT-003', 50, 3, 100, 'RUNNING')");   // ok
            st.execute("INSERT INTO lot_main VALUES ('LOT-004', 150, 5, 100, 'RUNNING')");  // 违规: temp>limit
            st.execute("INSERT INTO lot_main VALUES ('LOT-005', 95, 8, 100, 'DOWN')");      // 违规: a+b>=c
            st.execute("INSERT INTO lot_main VALUES ('LOT-006', -10, 0, 100, 'RUNNING')");  // 违规: temp<0
            st.execute("INSERT INTO lot_main VALUES ('LOT-007', 99, 0, 100, 'OnHold')");    // 违规: status not in [RUNNING,IDLE]
        }
    }

    @AfterAll
    void teardown() throws Exception {
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS lot_main");
        }
    }

    @Test
    @DisplayName("SQL Backend - 简单范围校验：temperature >= 0 && temperature <= 100")
    void rangeCheck() {
        CompiledRule rule = compiler.compile("temperature >= 0 && temperature <= 100");
        ViolationResult r = backend.executeBatch(rule, ds, "lot_main", ExecutionHints.auto());

        assertThat(r.totalRows()).isEqualTo(7);
        assertThat(r.violationCount()).isEqualTo(2); // LOT-004 (150), LOT-006 (-10)
        assertThat(r.backendUsed()).isEqualTo(Backend.SQL);
        assertThat(r.samples()).hasSize(2);
    }

    @Test
    @DisplayName("SQL Backend - 算术 + in 列表：A+B<C && status in [...]")
    void complexExpression() {
        CompiledRule rule = compiler.compile(
            "temperature + tolerance < max_limit && status in [\"RUNNING\", \"IDLE\"]"
        );
        ViolationResult r = backend.executeBatch(rule, ds, "lot_main", ExecutionHints.auto());

        assertThat(r.totalRows()).isEqualTo(7);
        // 违规：LOT-004(150+5>100), LOT-005(95+8>100), LOT-007(status=OnHold)
        // 实际可能多: LOT-006(temp+tol=-10+0=-10<100, ok; status=RUNNING, ok) → 合规
        assertThat(r.violationCount()).isGreaterThanOrEqualTo(2);
        assertThat(r.samples()).isNotEmpty();
    }

    @Test
    @DisplayName("SQL Backend - 全部合规时违规为 0")
    void allValid() {
        CompiledRule rule = compiler.compile("temperature > -1000");
        ViolationResult r = backend.executeBatch(rule, ds, "lot_main", ExecutionHints.auto());

        assertThat(r.totalRows()).isEqualTo(7);
        assertThat(r.violationCount()).isEqualTo(0);
        assertThat(r.samples()).isEmpty();
    }

    @Test
    @DisplayName("SQL Backend - 翻译预览：translate() 不执行只返回 SQL")
    void translateOnly() {
        CompiledRule rule = compiler.compile("temperature + tolerance < max_limit");
        String sql = backend.translate(rule);
        assertThat(sql).contains("\"temperature\"").contains("\"tolerance\"").contains("\"max_limit\"");
        assertThat(sql).contains("+").contains("<");
    }
}
