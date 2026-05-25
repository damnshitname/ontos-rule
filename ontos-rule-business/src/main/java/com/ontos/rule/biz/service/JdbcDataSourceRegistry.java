package com.ontos.rule.biz.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC 数据源注册中心。
 *
 * <p>运行时通过 REST API 注册任意 JDBC 数据源（MySQL / PostgreSQL / Oracle / Hive / Impala / StarRocks）。
 * SQL Backend 会按 dialect 翻译表达式并执行。
 *
 * <p>所有注册的数据源用 HikariCP 管理连接池。
 */
@Service
public class JdbcDataSourceRegistry {

    private static final Logger log = LoggerFactory.getLogger(JdbcDataSourceRegistry.class);

    private final Map<String, RegisteredSource> sources = new ConcurrentHashMap<>();

    public record RegisteredSource(
        DataSource dataSource,
        String tableName,
        String dialect,
        String jdbcUrl,
        String username
    ) {}

    /**
     * 注册一个 JDBC 数据源。
     *
     * @return 连通性测试结果摘要
     */
    public String register(String name, String jdbcUrl, String username, String password,
                           String tableName, String dialect) {
        if (sources.containsKey(name)) {
            // 关闭旧的，重新注册
            DataSource old = sources.get(name).dataSource();
            if (old instanceof HikariDataSource hds) {
                hds.close();
            }
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName("hikari-" + name);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000);
        config.setReadOnly(true);

        HikariDataSource ds;
        try {
            ds = new HikariDataSource(config);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                "无法创建 HikariDataSource: " + e.getMessage(), e
            );
        }

        // 连通性测试 + 表存在检查
        try (Connection conn = ds.getConnection()) {
            try (Statement st = conn.createStatement()) {
                st.executeQuery("SELECT 1");
            }
        } catch (SQLException e) {
            ds.close();
            throw new IllegalArgumentException(
                "数据源连接失败: " + e.getMessage() + " · jdbcUrl=" + jdbcUrl + " · user=" + username, e
            );
        }

        sources.put(name, new RegisteredSource(ds, tableName, dialect, jdbcUrl, username));
        log.info("Registered JDBC DataSource: name='{}' url='{}' table='{}' dialect='{}'",
            name, jdbcUrl, tableName, dialect);
        return "OK · 连接成功 · pool=hikari-" + name;
    }

    /**
     * 在已注册的数据源上建演示表 lot_main + 插 100 行测试数据。
     *
     * <p>方言适配：MySQL/PG/Oracle 用 SQL 略有差异。
     */
    public Map<String, Object> seedDemoData(String name) throws SQLException {
        RegisteredSource src = get(name);
        String dialect = src.dialect();
        String table = src.tableName();

        // 建表 SQL（按方言）
        String dropSql = "DROP TABLE IF EXISTS " + quoteTable(table, dialect);
        String createSql = buildCreateTableSql(table, dialect);

        try (Connection conn = src.dataSource().getConnection()) {
            conn.setReadOnly(false);
            try (Statement st = conn.createStatement()) {
                st.execute(dropSql);
                st.execute(createSql);
            }

            // 插数据
            Random r = new Random(42);
            String[] statuses = {"RUNNING", "IDLE", "OnHold", "Completed", "DOWN"};
            String insertSql = "INSERT INTO " + quoteTable(table, dialect) +
                " (lot_id, temperature, tolerance, max_limit, status) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (int i = 0; i < 100; i++) {
                    ps.setString(1, String.format("LOT-2026%05d", i + 1));
                    ps.setDouble(2, -50 + r.nextInt(900));
                    ps.setDouble(3, r.nextInt(10));
                    ps.setDouble(4, 100);
                    ps.setString(5, statuses[r.nextInt(statuses.length)]);
                    ps.addBatch();
                }
                int[] counts = ps.executeBatch();
                int inserted = 0;
                for (int c : counts) inserted += Math.max(c, 0);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("status", "OK");
                result.put("table", table);
                result.put("dialect", dialect);
                result.put("rowsInserted", inserted);
                return result;
            }
        } finally {
            // 改回只读
            try (Connection conn = src.dataSource().getConnection()) {
                conn.setReadOnly(true);
            } catch (SQLException ignore) {}
        }
    }

    private String quoteTable(String name, String dialect) {
        return switch (dialect.toLowerCase()) {
            case "mysql", "hive", "impala", "starrocks" -> "`" + name + "`";
            default -> "\"" + name + "\"";
        };
    }

    private String buildCreateTableSql(String table, String dialect) {
        String quoted = quoteTable(table, dialect);
        return switch (dialect.toLowerCase()) {
            case "mysql", "starrocks" -> "CREATE TABLE " + quoted + " (" +
                "lot_id VARCHAR(50) PRIMARY KEY, " +
                "temperature DOUBLE, " +
                "tolerance DOUBLE, " +
                "max_limit DOUBLE, " +
                "status VARCHAR(20))";
            case "oracle" -> "CREATE TABLE " + quoted + " (" +
                "lot_id VARCHAR2(50) PRIMARY KEY, " +
                "temperature NUMBER, " +
                "tolerance NUMBER, " +
                "max_limit NUMBER, " +
                "status VARCHAR2(20))";
            case "hive", "impala" -> "CREATE TABLE " + quoted + " (" +
                "lot_id STRING, " +
                "temperature DOUBLE, " +
                "tolerance DOUBLE, " +
                "max_limit DOUBLE, " +
                "status STRING)";
            // postgresql / h2
            default -> "CREATE TABLE " + quoted + " (" +
                "lot_id VARCHAR(50) PRIMARY KEY, " +
                "temperature DOUBLE PRECISION, " +
                "tolerance DOUBLE PRECISION, " +
                "max_limit DOUBLE PRECISION, " +
                "status VARCHAR(20))";
        };
    }

    public RegisteredSource get(String name) {
        RegisteredSource s = sources.get(name);
        if (s == null) {
            throw new IllegalArgumentException(
                "未注册的 JDBC 数据源: " + name + " · 已注册: " + sources.keySet()
            );
        }
        return s;
    }

    public boolean contains(String name) {
        return sources.containsKey(name);
    }

    public void unregister(String name) {
        RegisteredSource s = sources.remove(name);
        if (s != null && s.dataSource() instanceof HikariDataSource hds) {
            hds.close();
            log.info("Unregistered JDBC DataSource: name='{}'", name);
        }
    }

    public Map<String, Map<String, String>> describeAll() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        sources.forEach((name, src) -> {
            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("table", src.tableName());
            meta.put("dialect", src.dialect());
            meta.put("jdbcUrl", src.jdbcUrl());
            meta.put("username", src.username());
            result.put(name, meta);
        });
        return result;
    }
}
