package com.ontos.rule.biz.web;

import com.ontos.rule.biz.domain.RuleRun;
import com.ontos.rule.biz.service.ExecutionService;
import com.ontos.rule.biz.service.JdbcDataSourceRegistry;
import com.ontos.rule.biz.service.MockDataSource;
import com.ontos.rule.biz.web.dto.EvalRequest;
import com.ontos.rule.biz.web.dto.EvalResponse;
import com.ontos.rule.biz.web.dto.ExecuteRequest;
import com.ontos.rule.biz.web.dto.RegisterJdbcRequest;
import com.ontos.rule.biz.web.dto.RuleRunDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 规则执行 REST API。
 *
 * <p>所有调用支持 {@code X-Caller-Id} header 标识来源。
 * 不带 header 时默认 caller = "src-rest-api"。
 */
@RestController
@RequestMapping("/api")
public class ExecutionController {

    private final ExecutionService execService;
    private final MockDataSource mockDS;
    private final JdbcDataSourceRegistry jdbcRegistry;

    public ExecutionController(ExecutionService execService,
                               MockDataSource mockDS,
                               JdbcDataSourceRegistry jdbcRegistry) {
        this.execService = execService;
        this.mockDS = mockDS;
        this.jdbcRegistry = jdbcRegistry;
    }

    /**
     * 单条求值。
     *
     * <pre>{@code
     * POST /api/rules/QR-0001/eval
     * X-Caller-Id: src-my-platform
     * { "record": { "temperature": 92, "tolerance": 5, "maxLimit": 100 } }
     * }</pre>
     */
    @PostMapping("/rules/{ruleId}/eval")
    public EvalResponse eval(@PathVariable String ruleId,
                             @RequestBody EvalRequest req,
                             @RequestHeader(value = "X-Caller-Id", required = false) String caller) {
        ExecutionService.EvalOutcome out = execService.evalSingle(ruleId, req.record(), caller);
        return new EvalResponse(out.result(), out.latencyMs(), out.backend(), out.caller());
    }

    /**
     * 批量执行（在 mock 数据源上跑全表）。
     *
     * <pre>{@code
     * POST /api/rules/QR-0001/execute
     * { "dataSource": "mock-lot" }
     * }</pre>
     */
    @PostMapping("/rules/{ruleId}/execute")
    public RuleRunDto execute(@PathVariable String ruleId,
                              @Valid @RequestBody ExecuteRequest req,
                              @RequestHeader(value = "X-Caller-Id", required = false) String caller) {
        RuleRun run = execService.execute(ruleId, req.dataSource(), caller);
        return RuleRunDto.from(run);
    }

    /** 列出可用数据源：mock-* 走 JVM Backend / 注册的 JDBC 走 SQL Backend */
    @GetMapping("/data-sources")
    public Map<String, Object> dataSources() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jvm-mock", mockDS.listAvailable());
        result.put("sql-jdbc", jdbcRegistry.describeAll());
        return result;
    }

    /**
     * 注册一个 JDBC 数据源（MySQL / PG / Oracle / Hive 等）。
     */
    @PostMapping("/data-sources/jdbc")
    public Map<String, Object> registerJdbc(@Valid @RequestBody RegisterJdbcRequest req) {
        String summary = jdbcRegistry.register(
            req.name(), req.jdbcUrl(), req.username(), req.password(),
            req.tableName(), req.dialect()
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", req.name());
        result.put("dialect", req.dialect());
        result.put("table", req.tableName());
        result.put("connectivity", summary);
        return result;
    }

    /**
     * 在已注册的数据源上建演示表 lot_main + 插 100 行测试数据。
     * 方言适配建表 SQL（MySQL/PG/Oracle/Hive 不同）。
     */
    @PostMapping("/data-sources/jdbc/{name}/seed-demo")
    public Map<String, Object> seedDemo(@PathVariable String name) throws SQLException {
        return jdbcRegistry.seedDemoData(name);
    }

    /**
     * 注销 JDBC 数据源（关闭连接池）。
     */
    @DeleteMapping("/data-sources/jdbc/{name}")
    public Map<String, Object> unregisterJdbc(@PathVariable String name) {
        jdbcRegistry.unregister(name);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("status", "unregistered");
        return result;
    }
}
