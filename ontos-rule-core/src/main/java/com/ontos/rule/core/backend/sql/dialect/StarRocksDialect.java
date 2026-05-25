package com.ontos.rule.core.backend.sql.dialect;

/**
 * StarRocks 方言。Phase 1 stub。
 *
 * <p>MySQL 协议兼容，大部分函数复用 MySQL 实现。
 */
public class StarRocksDialect extends MysqlDialect {

    @Override
    public String name() {
        return "starrocks";
    }

    @Override
    public String lengthFunction(String arg) {
        // StarRocks 推荐 char_length
        return "char_length(" + arg + ")";
    }
}
