package com.ontos.rule.biz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontos.rule.biz.domain.Rule;
import com.ontos.rule.biz.domain.RuleRun;
import com.ontos.rule.biz.domain.RunStatus;
import com.ontos.rule.biz.domain.Violation;
import com.ontos.rule.biz.repo.RuleRepository;
import com.ontos.rule.biz.repo.RuleRunRepository;
import com.ontos.rule.biz.repo.ViolationRepository;
import com.ontos.rule.core.RuleEngine;
import com.ontos.rule.core.model.CompiledRule;
import com.ontos.rule.core.model.ExecutionHints;
import com.ontos.rule.core.model.InvocationContext;
import com.ontos.rule.core.model.ViolationResult;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 规则执行服务。
 *
 * <p>包装 core 的 eval/execute，负责：
 * <ul>
 *   <li>从 DB 取规则定义</li>
 *   <li>从 MockDataSource 取数据</li>
 *   <li>把执行结果持久化到 RuleRun + Violation 表</li>
 *   <li>把 caller 传给 core 的 InvocationContext（追溯）</li>
 * </ul>
 */
@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final RuleEngine engine;
    private final RuleRepository ruleRepo;
    private final RuleRunRepository runRepo;
    private final ViolationRepository vioRepo;
    private final MockDataSource mockDataSource;
    private final JdbcDataSourceRegistry jdbcRegistry;
    private final ObjectMapper mapper = new ObjectMapper();

    public ExecutionService(RuleEngine engine,
                            RuleRepository ruleRepo,
                            RuleRunRepository runRepo,
                            ViolationRepository vioRepo,
                            MockDataSource mockDataSource,
                            JdbcDataSourceRegistry jdbcRegistry) {
        this.engine = engine;
        this.ruleRepo = ruleRepo;
        this.runRepo = runRepo;
        this.vioRepo = vioRepo;
        this.mockDataSource = mockDataSource;
        this.jdbcRegistry = jdbcRegistry;
    }

    /**
     * 单条记录求值。
     */
    public EvalOutcome evalSingle(String ruleId, Map<String, Object> record, String caller) {
        Rule r = ruleRepo.findById(ruleId)
            .orElseThrow(() -> new EntityNotFoundException("规则不存在: " + ruleId));
        CompiledRule compiled = engine.compile(r.getExpression());

        String effectiveCaller = caller != null ? caller : "src-rest-api";
        InvocationContext ctx = new InvocationContext(
            effectiveCaller, "rest-user", r.getId(), java.util.UUID.randomUUID().toString());

        Instant start = Instant.now();
        boolean result = engine.eval(compiled, record, ctx);
        long latencyMs = Duration.between(start, Instant.now()).toMillis();

        return new EvalOutcome(result, latencyMs, "JVM", effectiveCaller);
    }

    /**
     * 批量执行。
     *
     * <p>路由策略：
     * <ul>
     *   <li>dataSource 注册在 JdbcDataSourceRegistry → 走 SQL Backend（下推数据库）</li>
     *   <li>否则按 MockDataSource 处理 → 走 JVM Backend（内存 List）</li>
     * </ul>
     */
    @Transactional
    public RuleRun execute(String ruleId, String dataSourceName, String caller) {
        Rule r = ruleRepo.findById(ruleId)
            .orElseThrow(() -> new EntityNotFoundException("规则不存在: " + ruleId));
        CompiledRule compiled = engine.compile(r.getExpression());

        String effectiveCaller = caller != null ? caller : "src-rest-api";

        RuleRun run = new RuleRun();
        run.setRuleId(ruleId);
        run.setStartedAt(Instant.now());
        run.setDataSource(dataSourceName);
        run.setCaller(effectiveCaller);
        run.setStatus(RunStatus.RUNNING);
        run = runRepo.save(run);

        Instant start = Instant.now();
        try {
            InvocationContext ctx = new InvocationContext(
                effectiveCaller, "rest-user", ruleId,
                java.util.UUID.randomUUID().toString());

            ViolationResult result;
            if (jdbcRegistry.contains(dataSourceName)) {
                // SQL Backend 路径
                JdbcDataSourceRegistry.RegisteredSource src = jdbcRegistry.get(dataSourceName);
                result = engine.executeOnSql(
                    compiled, src.dataSource(), src.tableName(), src.dialect(),
                    ExecutionHints.auto(), ctx
                );
            } else {
                // JVM Backend 路径
                List<Map<String, Object>> rows = mockDataSource.load(dataSourceName);
                result = engine.execute(compiled, rows, ExecutionHints.auto(), ctx);
            }

            run.setFinishedAt(Instant.now());
            run.setDurationMs(result.elapsed().toMillis());
            run.setTotalRows(result.totalRows());
            run.setViolationCount(result.violationCount());
            run.setBackendUsed(result.backendUsed().name());
            run.setStatus(RunStatus.SUCCESS);
            run = runRepo.save(run);

            // 持久化违规样本
            for (Map<String, Object> sample : result.samples()) {
                Violation v = new Violation();
                v.setRunId(run.getId());
                Object pk = sample.values().iterator().next();
                v.setTargetPk(pk != null ? pk.toString() : "(unknown)");
                String valStr = sample.toString();
                v.setViolatingValue(valStr.length() > 500 ? valStr.substring(0, 500) : valStr);
                v.setContext(serialize(sample));
                v.setSampledAt(Instant.now());
                vioRepo.save(v);
            }
            log.info("rule {} executed on {} [{}]: {} violations / {} rows in {}ms",
                ruleId, dataSourceName, result.backendUsed(),
                result.violationCount(), result.totalRows(), result.elapsed().toMillis());

        } catch (RuntimeException e) {
            run.setStatus(RunStatus.FAILED);
            run.setError(e.getMessage());
            run.setFinishedAt(Instant.now());
            run.setDurationMs(Duration.between(start, Instant.now()).toMillis());
            log.error("rule {} execution failed", ruleId, e);
            return runRepo.save(run);
        }

        return run;
    }

    private String serialize(Map<String, Object> map) {
        try {
            return mapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return map.toString();
        }
    }

    /** eval 结果 + 元信息 */
    public record EvalOutcome(boolean result, long latencyMs, String backend, String caller) {}
}
