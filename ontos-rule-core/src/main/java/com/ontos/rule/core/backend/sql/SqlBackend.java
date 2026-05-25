package com.ontos.rule.core.backend.sql;

import com.ontos.rule.core.backend.ExecutionBackend;
import com.ontos.rule.core.backend.sql.dialect.SqlDialect;
import com.ontos.rule.core.backend.sql.translator.CelToSqlTranslator;
import com.ontos.rule.core.model.Backend;
import com.ontos.rule.core.model.CompiledRule;
import com.ontos.rule.core.model.ExecutionHints;
import com.ontos.rule.core.model.ViolationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL 下推 Backend。
 *
 * <p>核心思路：把 CEL 表达式翻译成 SQL WHERE 子句，让数据库自己跑。
 * 数据不出库，只把"违规计数 + 样本"传回。
 *
 * <p>翻译示例：
 * <pre>
 * CEL:  temperature + tolerance &lt; maxLimit && status in ["RUNNING","IDLE"]
 * SQL:  WHERE NOT (
 *           (temperature + tolerance &lt; maxLimit)
 *           AND status IN ('RUNNING','IDLE')
 *       )
 * </pre>
 *
 * <p><b>支持的方言（按优先级）：</b>
 * <table border="1">
 *   <tr><th>P</th><th>方言</th><th>备注</th></tr>
 *   <tr><td>P0</td><td>PostgreSQL</td><td>基线方言，H2 PG mode 兼容</td></tr>
 *   <tr><td>P0</td><td>MySQL</td><td>反引号 + REGEXP + CONCAT</td></tr>
 *   <tr><td>P0</td><td>Oracle</td><td>FETCH FIRST n ROWS ONLY 代替 LIMIT</td></tr>
 *   <tr><td>P1</td><td>Hive</td><td>RLIKE + concat</td></tr>
 *   <tr><td>P1</td><td>StarRocks</td><td>MySQL 兼容</td></tr>
 *   <tr><td>P1</td><td>Impala</td><td>regexp_like 等</td></tr>
 *   <tr><td>P2</td><td>Kudu</td><td>通过 Impala 访问</td></tr>
 * </table>
 */
public class SqlBackend implements ExecutionBackend {

    private static final Logger log = LoggerFactory.getLogger(SqlBackend.class);

    private final SqlDialect dialect;
    private final CelToSqlTranslator translator;

    public SqlBackend(SqlDialect dialect) {
        this.dialect = dialect;
        this.translator = new CelToSqlTranslator(dialect);
    }

    public SqlDialect dialect() {
        return dialect;
    }

    @Override
    public Backend kind() {
        return Backend.SQL;
    }

    @Override
    public boolean eval(CompiledRule rule, Map<String, Object> record) {
        throw new UnsupportedOperationException(
            "SQL Backend 不支持单条 eval · 单条求值请用 JvmBackend"
        );
    }

    /**
     * 把 CEL 表达式翻译为 SQL WHERE 子句（供调试/预览）。
     */
    public String translate(CompiledRule rule) {
        return translator.translate(rule.ast());
    }

    /**
     * 批量执行：在数据库上跑 SQL，返回违规计数 + 采样。
     *
     * @param rule        已编译规则
     * @param dataSource  JDBC DataSource
     * @param tableName   目标表名
     * @param hints       执行提示（sampleLimit / timeout）
     */
    public ViolationResult executeBatch(CompiledRule rule,
                                        DataSource dataSource,
                                        String tableName,
                                        ExecutionHints hints) {
        String whereClause = translator.translate(rule.ast());
        String countSql = dialect.buildCountQuery(tableName, whereClause);
        String sampleSql = dialect.buildSampleQuery(tableName, whereClause, hints.sampleLimit());

        log.debug("SQL Backend executing on {} · dialect={} · count SQL: {}",
            tableName, dialect.name(), countSql);

        Instant start = Instant.now();
        try (Connection conn = dataSource.getConnection()) {
            conn.setReadOnly(true);

            // 1. 总数 + 违规数
            long totalRows;
            long violationCount;
            try (PreparedStatement stmt = conn.prepareStatement(countSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new RuntimeException("SQL Backend count query returned no rows");
                }
                totalRows = rs.getLong(1);
                violationCount = rs.getLong(2);
            }

            // 2. 采样违规
            List<Map<String, Object>> samples = new ArrayList<>();
            if (violationCount > 0 && hints.sampleLimit() > 0) {
                try (PreparedStatement stmt = conn.prepareStatement(sampleSql);
                     ResultSet rs = stmt.executeQuery()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>(colCount);
                        for (int i = 1; i <= colCount; i++) {
                            row.put(meta.getColumnLabel(i), rs.getObject(i));
                        }
                        samples.add(row);
                        if (samples.size() >= hints.sampleLimit()) {
                            break;
                        }
                    }
                }
            }

            Duration elapsed = Duration.between(start, Instant.now());
            log.info("SQL Backend done · {}/{} violations · {} samples · {}ms · table={} · dialect={}",
                violationCount, totalRows, samples.size(), elapsed.toMillis(), tableName, dialect.name());

            return new ViolationResult(totalRows, violationCount, samples, Backend.SQL, elapsed);

        } catch (SQLException e) {
            throw new RuntimeException(
                "SQL Backend 执行失败 · table=" + tableName + " · dialect=" + dialect.name() +
                " · sqlState=" + e.getSQLState() + " · " + e.getMessage(), e
            );
        }
    }
}
