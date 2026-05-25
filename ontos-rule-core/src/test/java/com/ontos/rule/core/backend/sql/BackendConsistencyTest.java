package com.ontos.rule.core.backend.sql;

import com.ontos.rule.core.backend.jvm.JvmBackend;
import com.ontos.rule.core.backend.sql.dialect.PostgresqlDialect;
import com.ontos.rule.core.compiler.CelCompiler;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Backend 一致性测试 ⭐ 核心保障。
 *
 * <p>同一份规则 + 同一份数据，<b>JVM Backend 和 SQL Backend 必须返回完全相同的违规计数</b>。
 *
 * <p>方法：
 * <ol>
 *   <li>生成 1000 行随机数据</li>
 *   <li>同时插入 H2（供 SQL 用）和内存 List（供 JVM 用）</li>
 *   <li>对多条规则分别跑两个 Backend</li>
 *   <li>断言 violationCount 完全一致</li>
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BackendConsistencyTest {

    private DataSource ds;
    private List<Map<String, Object>> inMemoryRows;
    private final CelCompiler compiler = new CelCompiler();
    private final JvmBackend jvm = new JvmBackend();
    private final SqlBackend sql = new SqlBackend(new PostgresqlDialect());

    @BeforeAll
    void setup() throws Exception {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:consistency-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1");
        h2.setUser("sa");
        h2.setPassword("");
        this.ds = h2;

        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS wafer_data");
            st.execute("""
                CREATE TABLE wafer_data (
                    id INT PRIMARY KEY,
                    temperature DOUBLE,
                    tolerance DOUBLE,
                    max_limit DOUBLE,
                    status VARCHAR(20)
                )
                """);
        }

        Random r = new Random(42);
        String[] statuses = {"RUNNING", "IDLE", "DOWN", "MAINT", "OFF"};
        inMemoryRows = new ArrayList<>(1000);

        try (Connection conn = ds.getConnection();
             var ps = conn.prepareStatement("INSERT INTO wafer_data VALUES (?, ?, ?, ?, ?)")) {
            for (int i = 0; i < 1000; i++) {
                long temp = -50 + r.nextInt(900);  // -50 ~ 850
                long tol = r.nextInt(20);
                long limit = 100;
                String status = statuses[r.nextInt(statuses.length)];

                // 内存
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", (long) i);
                row.put("temperature", temp);
                row.put("tolerance", tol);
                row.put("max_limit", limit);
                row.put("status", status);
                inMemoryRows.add(row);

                // H2
                ps.setInt(1, i);
                ps.setDouble(2, temp);
                ps.setDouble(3, tol);
                ps.setDouble(4, limit);
                ps.setString(5, status);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @AfterAll
    void teardown() throws Exception {
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS wafer_data");
        }
    }

    @Test
    @DisplayName("一致性：简单范围校验 temperature >= 0 && temperature <= 100")
    void rangeCheck() {
        assertConsistent("temperature >= 0 && temperature <= 100");
    }

    @Test
    @DisplayName("一致性：算术 + 比较 temperature + tolerance < max_limit")
    void arithmetic() {
        assertConsistent("temperature + tolerance < max_limit");
    }

    @Test
    @DisplayName("一致性：组合表达式 (算术 + 逻辑 + in)")
    void complex() {
        assertConsistent(
            "temperature + tolerance < max_limit && status in [\"RUNNING\", \"IDLE\"]"
        );
    }

    @Test
    @DisplayName("一致性：全合规规则 (违规=0)")
    void allValid() {
        assertConsistent("temperature > -10000");
    }

    @Test
    @DisplayName("一致性：全违规规则 (违规=1000)")
    void allInvalid() {
        assertConsistent("temperature > 10000");
    }

    private void assertConsistent(String celExpression) {
        CompiledRule rule = compiler.compile(celExpression);

        ViolationResult jvmResult = jvm.executeBatch(rule, inMemoryRows, ExecutionHints.jvm());
        ViolationResult sqlResult = sql.executeBatch(rule, ds, "wafer_data", ExecutionHints.sql());

        assertThat(sqlResult.totalRows())
            .as("totalRows 一致性 · CEL: " + celExpression)
            .isEqualTo(jvmResult.totalRows());

        assertThat(sqlResult.violationCount())
            .as("violationCount 一致性 · CEL: " + celExpression +
                " · JVM=" + jvmResult.violationCount() + " SQL=" + sqlResult.violationCount())
            .isEqualTo(jvmResult.violationCount());
    }
}
